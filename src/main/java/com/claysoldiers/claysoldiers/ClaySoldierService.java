package com.claysoldiers.claysoldiers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

public final class ClaySoldierService {
    private static final String SOLDIER_ENTITY_TYPE = "clay_soldier";
    private static final double MIN_MOVE_DISTANCE_SQUARED = 0.0001D;

    private final Plugin plugin;
    private final ClaySoldierItems items;
    private final ClaySoldierSettings settings;
    private final NamespacedKey entityTypeKey;
    private final NamespacedKey teamKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey modifiersKey;
    private final NamespacedKey healthKey;
    private final Set<UUID> activeSoldiers = new HashSet<>();
    private final Map<UUID, SoldierBrain> brains = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask tickTask;

    public ClaySoldierService(Plugin plugin, ClaySoldierItems items, ClaySoldierSettings settings) {
        this.plugin = plugin;
        this.items = items;
        this.settings = settings;
        this.entityTypeKey = new NamespacedKey(plugin, "clay_entity_type");
        this.teamKey = new NamespacedKey(plugin, "clay_team");
        this.roleKey = new NamespacedKey(plugin, "clay_role");
        this.modifiersKey = new NamespacedKey(plugin, "clay_modifiers");
        this.healthKey = new NamespacedKey(plugin, "clay_health");
    }

    public Plugin plugin() {
        return this.plugin;
    }

    public void start() {
        registerLoadedSoldiers();
        this.tickTask = this.plugin.getServer().getScheduler().runTaskTimer(
                this.plugin,
                this::tick,
                this.settings.tickPeriodTicks(),
                this.settings.tickPeriodTicks()
        );
    }

    public void shutdown() {
        if( this.tickTask != null ) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        this.brains.clear();
        this.activeSoldiers.clear();
    }

    public int activeCount() {
        return this.activeSoldiers.size();
    }

    public int nearbySoldierCount(Location origin) {
        if( !this.settings.spawnLimitEnabled() ) {
            return 0;
        }

        double radiusSquared = this.settings.spawnLimitRadius() * this.settings.spawnLimitRadius();
        int count = 0;
        for( UUID id : List.copyOf(this.activeSoldiers) ) {
            Entity entity = this.plugin.getServer().getEntity(id);
            if( !(entity instanceof ArmorStand stand) || stand.isDead() || !isSoldier(stand) ) {
                forgetSoldier(id);
                continue;
            }

            if( stand.getWorld().equals(origin.getWorld()) && stand.getLocation().distanceSquared(origin) <= radiusSquared ) {
                count++;
            }
        }

        return count;
    }

    public boolean canSpawnSoldiers(Location origin, int count) {
        return !this.settings.spawnLimitEnabled()
                || nearbySoldierCount(origin) + count <= this.settings.spawnLimitMaxSoldiers();
    }

    public int spawnLimitMaxSoldiers() {
        return this.settings.spawnLimitMaxSoldiers();
    }

    public double spawnLimitRadius() {
        return this.settings.spawnLimitRadius();
    }

    public void registerLoadedSoldiers() {
        for( World world : this.plugin.getServer().getWorlds() ) {
            for( ArmorStand armorStand : world.getEntitiesByClass(ArmorStand.class) ) {
                registerIfSoldier(armorStand);
            }
        }
    }

    public void registerIfSoldier(Entity entity) {
        if( entity instanceof ArmorStand armorStand && isSoldier(armorStand) ) {
            this.activeSoldiers.add(armorStand.getUniqueId());
            this.brains.computeIfAbsent(armorStand.getUniqueId(), ignored -> new SoldierBrain());
            updateNameplate(armorStand);
        }
    }

    public boolean isSoldier(Entity entity) {
        if( !(entity instanceof ArmorStand armorStand) ) {
            return false;
        }

        String type = armorStand.getPersistentDataContainer().get(this.entityTypeKey, PersistentDataType.STRING);
        return SOLDIER_ENTITY_TYPE.equals(type);
    }

    public Optional<ClayTeam> getTeam(Entity entity) {
        if( !isSoldier(entity) ) {
            return Optional.empty();
        }

        String teamKeyValue = entity.getPersistentDataContainer().get(this.teamKey, PersistentDataType.STRING);
        if( teamKeyValue == null ) {
            return Optional.empty();
        }

        return ClayTeam.fromKey(teamKeyValue);
    }

    public ClaySoldierRole getRole(Entity entity) {
        if( !isSoldier(entity) ) {
            return ClaySoldierRole.WARRIOR;
        }

        String roleKeyValue = entity.getPersistentDataContainer().get(this.roleKey, PersistentDataType.STRING);
        if( roleKeyValue == null ) {
            return ClaySoldierRole.WARRIOR;
        }

        ClaySoldierRole role = ClaySoldierRole.fromKey(roleKeyValue).orElse(ClaySoldierRole.WARRIOR);
        return this.settings.role(role).enabled() ? role : ClaySoldierRole.WARRIOR;
    }

    public List<ArmorStand> spawnSoldiers(ClayTeam team, Location baseLocation, int count) {
        return spawnSoldiers(team, ClaySoldierRole.WARRIOR, baseLocation, count);
    }

    public List<ArmorStand> spawnSoldiers(ClayTeam team, ClaySoldierRole role, Location baseLocation, int count) {
        return spawnSoldiers(team, role, baseLocation, count, Set.of());
    }

    public List<ArmorStand> spawnSoldiers(ClayTeam team, ClaySoldierRole role, Location baseLocation, int count, Set<ClaySoldierModifier> modifiers) {
        int boundedCount = Math.max(1, Math.min(this.settings.maxSpawnPerUse(), count));
        if( !canSpawnSoldiers(baseLocation, boundedCount) ) {
            throw new IllegalStateException("Clay soldier spawn limit reached");
        }

        return java.util.stream.IntStream.range(0, boundedCount)
                .mapToObj(i -> spawnSoldier(team, role, randomizedSpawn(baseLocation), modifiers))
                .toList();
    }

    public ArmorStand spawnSoldier(ClayTeam team, Location location) {
        return spawnSoldier(team, ClaySoldierRole.WARRIOR, location);
    }

    public ArmorStand spawnSoldier(ClayTeam team, ClaySoldierRole role, Location location) {
        return spawnSoldier(team, role, location, Set.of());
    }

    public ArmorStand spawnSoldier(ClayTeam team, ClaySoldierRole role, Location location, Set<ClaySoldierModifier> modifiers) {
        World world = location.getWorld();
        if( world == null ) {
            throw new IllegalArgumentException("Cannot spawn a clay soldier without a world");
        }

        if( !this.settings.role(role).enabled() ) {
            role = ClaySoldierRole.WARRIOR;
        }

        ArmorStand stand = world.spawn(location, ArmorStand.class);
        configureSoldier(stand, team, role, modifiers);
        this.activeSoldiers.add(stand.getUniqueId());
        this.brains.put(stand.getUniqueId(), new SoldierBrain());
        playSound(stand.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.0F, 1.0F + this.random.nextFloat() * this.settings.soundPitchJitter());
        spawnParticle(Particle.DUST, stand.getLocation().clone().add(0.0D, 0.4D, 0.0D), scaledCount(8), 0.12D, 0.12D, 0.12D,
                new Particle.DustOptions(team.armorColor(), 0.7F));
        return stand;
    }

    public void damageSoldier(ArmorStand soldier, double damage) {
        damageSoldier(soldier, damage, null);
    }

    public void damageSoldier(ArmorStand soldier, double damage, Entity attacker) {
        if( !isSoldier(soldier) || soldier.isDead() ) {
            return;
        }

        ClaySoldierRole role = getRole(soldier);
        ClaySoldierSettings.RoleTuning roleTuning = this.settings.role(role);
        if( attacker instanceof ArmorStand attackingSoldier && isSoldier(attackingSoldier) && tryDodge(soldier, attackingSoldier, roleTuning) ) {
            return;
        }

        double health = getHealth(soldier) - Math.max(0.0D, damage);
        if( health <= 0.0D ) {
            killSoldier(soldier);
            return;
        }

        soldier.getPersistentDataContainer().set(this.healthKey, PersistentDataType.DOUBLE, health);
        updateNameplate(soldier);
        playSound(soldier.getLocation(), Sound.ENTITY_ARMOR_STAND_HIT, 0.55F, 1.25F);
        SoldierBrain brain = brain(soldier);
        brain.animation = AnimationState.HIT;
        brain.animationTicks = 8;

        getTeam(soldier).ifPresent(team -> spawnParticle(Particle.DUST,
                soldier.getLocation().clone().add(0.0D, 0.6D, 0.0D),
                scaledCount(3),
                0.08D,
                0.08D,
                0.08D,
                new Particle.DustOptions(team.armorColor(), 0.5F)
        ));
    }

    public int clearSoldiers(Location origin, double radius) {
        double radiusSquared = radius * radius;
        int removed = 0;

        for( UUID id : List.copyOf(this.activeSoldiers) ) {
            Entity entity = this.plugin.getServer().getEntity(id);
            if( !(entity instanceof ArmorStand stand) || !isSoldier(stand) ) {
                forgetSoldier(id);
                continue;
            }

            if( stand.getWorld().equals(origin.getWorld()) && stand.getLocation().distanceSquared(origin) <= radiusSquared ) {
                removeSoldier(stand);
                removed++;
            }
        }

        return removed;
    }

    private void configureSoldier(ArmorStand stand, ClayTeam team, ClaySoldierRole role, Set<ClaySoldierModifier> modifiers) {
        stand.setSmall(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(true);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        stand.setCollidable(false);

        PersistentDataContainer data = stand.getPersistentDataContainer();
        data.set(this.entityTypeKey, PersistentDataType.STRING, SOLDIER_ENTITY_TYPE);
        data.set(this.teamKey, PersistentDataType.STRING, team.key());
        data.set(this.roleKey, PersistentDataType.STRING, role.key());
        data.set(this.modifiersKey, PersistentDataType.STRING, serializeModifiers(modifiers));
        ClaySoldierSettings.RoleTuning roleTuning = this.settings.role(role);
        data.set(this.healthKey, PersistentDataType.DOUBLE, maxHealth(role, modifiers));
        updateNameplate(stand);

        EntityEquipment equipment = stand.getEquipment();
        if( equipment != null ) {
            equipment.setHelmet(this.items.createArmorPiece(Material.LEATHER_HELMET, team));
            equipment.setChestplate(this.items.createArmorPiece(Material.LEATHER_CHESTPLATE, team));
            equipment.setLeggings(this.items.createArmorPiece(Material.LEATHER_LEGGINGS, team));
            equipment.setBoots(this.items.createArmorPiece(Material.LEATHER_BOOTS, team));
            equipment.setItemInMainHand(itemFor(roleTuning.mainHand()));
            equipment.setItemInOffHand(itemFor(roleTuning.offHand()));
        }
    }

    private Location randomizedSpawn(Location baseLocation) {
        Location location = baseLocation.clone();
        location.add((this.random.nextDouble() - 0.5D) * 0.35D, 0.0D, (this.random.nextDouble() - 0.5D) * 0.35D);
        location.setYaw(this.random.nextFloat() * 360.0F);
        location.setPitch(0.0F);
        return location;
    }

    private void tick() {
        for( UUID id : List.copyOf(this.activeSoldiers) ) {
            Entity entity = this.plugin.getServer().getEntity(id);
            if( !(entity instanceof ArmorStand soldier) || soldier.isDead() || !isSoldier(soldier) ) {
                forgetSoldier(id);
                continue;
            }

            SoldierBrain brain = brain(soldier);
            ClaySoldierRole role = getRole(soldier);
            brain.tick(this.settings.tickPeriodTicks());
            updateFormationState(soldier, brain, role);

            if( brain.windupTicks > 0 ) {
                tickWindup(soldier, brain, role);
            } else {
                Optional<ArmorStand> nearestEnemy = findNearestEnemy(soldier, role);
                if( nearestEnemy.isPresent() ) {
                    engage(soldier, nearestEnemy.get(), brain, role);
                } else if( brain.activeFormation.isActive() ) {
                    holdFormation(soldier, brain, role);
                } else {
                    wander(soldier, brain, role);
                }
            }

            applyPose(soldier, brain, role);
            brain.movedThisTick = false;
        }
    }

    private void updateFormationState(ArmorStand soldier, SoldierBrain brain, ClaySoldierRole role) {
        TacticalFormation previousFormation = brain.activeFormation;
        brain.activeFormation = tacticalFormation(soldier, role);
        if( previousFormation != brain.activeFormation ) {
            updateNameplate(soldier);
            if( previousFormation == TacticalFormation.NONE && brain.activeFormation.isActive() && this.settings.formationSoundEnabled() ) {
                playSound(soldier.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.55F, 1.65F);
                spawnParticle(Particle.HAPPY_VILLAGER, soldier.getLocation().clone().add(0.0D, 0.85D, 0.0D), scaledCount(2), 0.06D, 0.05D, 0.06D, 0.0D);
            }
        }
    }

    private void tickWindup(ArmorStand soldier, SoldierBrain brain, ClaySoldierRole role) {
        Entity targetEntity = brain.queuedTargetId == null ? null : this.plugin.getServer().getEntity(brain.queuedTargetId);
        if( !(targetEntity instanceof ArmorStand target) || target.isDead() || !isSoldier(target) ) {
            brain.clearQueuedAttack();
            return;
        }

        face(soldier, target.getLocation());
        brain.windupTicks -= this.settings.tickPeriodTicks();
        if( brain.windupTicks <= 0 ) {
            performAttack(soldier, target, brain, role);
            brain.clearQueuedAttack();
        }
    }

    private Optional<ArmorStand> findNearestEnemy(ArmorStand soldier, ClaySoldierRole role) {
        Optional<ClayTeam> soldierTeam = getTeam(soldier);
        if( soldierTeam.isEmpty() ) {
            return Optional.empty();
        }

        ArmorStand nearest = null;
        double searchRange = Math.max(this.settings.followRange(), attackRange(soldier, role) + this.settings.targetSearchExtraRange());
        double nearestDistanceSquared = searchRange * searchRange;

        for( UUID id : this.activeSoldiers ) {
            Entity entity = this.plugin.getServer().getEntity(id);
            if( !(entity instanceof ArmorStand candidate) || candidate.equals(soldier) || candidate.isDead() || !candidate.getWorld().equals(soldier.getWorld()) ) {
                continue;
            }

            Optional<ClayTeam> candidateTeam = getTeam(candidate);
            if( candidateTeam.isEmpty() || candidateTeam.get() == soldierTeam.get() ) {
                continue;
            }

            double distanceSquared = candidate.getLocation().distanceSquared(soldier.getLocation());
            if( distanceSquared < nearestDistanceSquared ) {
                nearest = candidate;
                nearestDistanceSquared = distanceSquared;
            }
        }

        return Optional.ofNullable(nearest);
    }

    private void engage(ArmorStand soldier, ArmorStand target, SoldierBrain brain, ClaySoldierRole role) {
        double distanceSquared = soldier.getLocation().distanceSquared(target.getLocation());
        double attackRange = attackRange(soldier, role);
        ClaySoldierSettings.RoleTuning roleTuning = this.settings.role(role);
        TacticalFormation tacticalFormation = brain.activeFormation;
        face(soldier, target.getLocation());

        if( distanceSquared <= attackRange * attackRange && brain.attackCooldownTicks <= 0 && hasAttackLineOfSight(soldier, target) ) {
            queueAttack(soldier, target, brain, role, Math.sqrt(distanceSquared));
            return;
        }

        if( tacticalFormation.isWall() ) {
            brain.flankTicks = 0;
        }

        if( !tacticalFormation.isActive() && this.settings.useFlanking() && brain.flankTicks <= 0 && this.random.nextDouble() < roleTuning.flankChance() * this.settings.flankChanceScale() ) {
            brain.startFlank(this.random, this.settings);
        }

        Location goal = brain.flankTicks > 0 && !tacticalFormation.isActive()
                ? flankPosition(soldier, target, brain)
                : formationPosition(soldier, target, role, tacticalFormation);
        double step = this.settings.moveStep() * speedMultiplier(soldier, roleTuning);
        if( tacticalFormation == TacticalFormation.SHIELD_WALL ) {
            step *= this.settings.shieldWallSpeedMultiplier();
        } else if( tacticalFormation.isActive() ) {
            step *= this.settings.tacticalFormationSpeedMultiplier();
        }

        if( distanceSquared < (attackRange + 0.8D) * (attackRange + 0.8D) && role != ClaySoldierRole.GUARD ) {
            step *= this.settings.closeCombatStepMultiplier();
        }

        if( moveToward(soldier, brain, goal, step) ) {
            brain.movedThisTick = true;
            maybeHop(soldier, this.settings.chaseJumpChance());
            if( brain.animationTicks <= 0 ) {
                brain.animation = AnimationState.RUN;
                brain.animationTicks = this.settings.tickPeriodTicks() + 3;
            }
        }
    }

    private void holdFormation(ArmorStand soldier, SoldierBrain brain, ClaySoldierRole role) {
        Optional<Location> centroid = formationCentroid(soldier, role);
        if( centroid.isEmpty() ) {
            return;
        }

        Location target = centroid.get();
        faceDirection(soldier, horizontalDirection(soldier.getLocation(), target));
        if( soldier.getLocation().distanceSquared(target) <= this.settings.shieldWallHoldRadius() * this.settings.shieldWallHoldRadius() ) {
            return;
        }

        double step = this.settings.moveStep() * speedMultiplier(soldier, this.settings.role(role));
        if( brain.activeFormation == TacticalFormation.SHIELD_WALL ) {
            step *= this.settings.shieldWallSpeedMultiplier();
        } else {
            step *= this.settings.tacticalFormationSpeedMultiplier();
        }

        if( moveToward(soldier, brain, target, step) ) {
            brain.movedThisTick = true;
            if( brain.animationTicks <= 0 ) {
                brain.animation = AnimationState.RUN;
                brain.animationTicks = this.settings.tickPeriodTicks() + 3;
            }
        }
    }

    private void queueAttack(ArmorStand soldier, ArmorStand target, SoldierBrain brain, ClaySoldierRole role, double distance) {
        AttackStyle style = chooseAttackStyle(soldier, target, role, distance);
        ClaySoldierSettings.AttackTuning tuning = this.settings.attack(style.key);
        int cooldownTicks = (int) Math.round((this.settings.attackCooldownTicks() + tuning.extraCooldownTicks()) * cooldownMultiplier(soldier));
        brain.queuedAttack = style;
        brain.queuedTargetId = target.getUniqueId();
        brain.windupTicks = tuning.windupTicks();
        brain.attackCooldownTicks = Math.max(this.settings.tickPeriodTicks(), cooldownTicks);
        brain.animation = style.animation;
        brain.animationTicks = tuning.windupTicks() + 8;

        if( style == AttackStyle.LEAP ) {
            leapToward(soldier, target);
        } else {
            maybeHop(soldier, this.settings.attackLeapChance());
        }

        playSound(soldier.getLocation(), style.windupSound, 0.45F, 1.0F + this.random.nextFloat() * this.settings.soundPitchJitter());
    }

    private AttackStyle chooseAttackStyle(ArmorStand soldier, ArmorStand target, ClaySoldierRole role, double distance) {
        if( role == ClaySoldierRole.SLINGER && enabled(AttackStyle.SLING) ) {
            return AttackStyle.SLING;
        }
        if( role == ClaySoldierRole.SPEARMAN && enabled(AttackStyle.POKE) ) {
            return AttackStyle.POKE;
        }
        if( role == ClaySoldierRole.GUARD && enabled(AttackStyle.SHIELD_BASH) && this.random.nextDouble() < this.settings.guardShieldBashChance() ) {
            return AttackStyle.SHIELD_BASH;
        }
        if( enabled(AttackStyle.SWEEP) && nearbyEnemies(target, getTeam(soldier).orElse(ClayTeam.CLAY), this.settings.sweepClusterRadius()) >= this.settings.sweepMinEnemies()
                && this.random.nextDouble() < this.settings.sweepChance() ) {
            return AttackStyle.SWEEP;
        }
        if( enabled(AttackStyle.LEAP) && distance > this.settings.leapMinDistance()
                && this.random.nextDouble() < (role == ClaySoldierRole.SKIRMISHER ? this.settings.skirmisherLeapChance() : this.settings.warriorLeapChance()) ) {
            return AttackStyle.LEAP;
        }

        return AttackStyle.QUICK;
    }

    private void performAttack(ArmorStand soldier, ArmorStand target, SoldierBrain brain, ClaySoldierRole role) {
        AttackStyle style = brain.queuedAttack == null ? AttackStyle.QUICK : brain.queuedAttack;
        ClaySoldierSettings.AttackTuning attackTuning = this.settings.attack(style.key);
        ClaySoldierSettings.RoleTuning roleTuning = this.settings.role(role);
        double maxRange = attackRange(soldier, role) + attackTuning.rangeBonus();
        if( soldier.getLocation().distanceSquared(target.getLocation()) > maxRange * maxRange ) {
            return;
        }
        if( !hasAttackLineOfSight(soldier, target) ) {
            return;
        }

        TacticalFormation tacticalFormation = brain.activeFormation;
        double damage = this.settings.attackDamage() * damageMultiplier(soldier, roleTuning) * attackTuning.damageMultiplier();
        if( tacticalFormation.isWall() ) {
            damage *= this.settings.tacticalFormationDamageMultiplier();
        }
        soldier.swingMainHand();

        switch( style ) {
            case SLING -> {
                drawSlingTrail(soldier, target);
                damageSoldier(target, damage, soldier);
                playSound(soldier.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.7F, 1.35F);
            }
            case SWEEP -> {
                damageSoldier(target, damage, soldier);
                for( ArmorStand enemy : nearbyEnemySoldiers(target, getTeam(soldier).orElse(ClayTeam.CLAY), this.settings.sweepClusterRadius() + 0.10D) ) {
                    if( !enemy.equals(target) ) {
                        damageSoldier(enemy, damage * this.settings.sweepSplashDamageMultiplier(), soldier);
                    }
                }
                spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().clone().add(0.0D, 0.55D, 0.0D), scaledCount(1));
                playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7F, 1.6F);
            }
            case SHIELD_BASH -> {
                damageSoldier(target, damage, soldier);
                pushAway(target, soldier.getLocation(), this.settings.shieldBashPushDistance());
                playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.75F, 1.1F);
            }
            case LEAP -> {
                damageSoldier(target, damage, soldier);
                spawnParticle(Particle.CLOUD, soldier.getLocation(), scaledCount(4), 0.08D, 0.04D, 0.08D, 0.01D);
                playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.55F, 1.4F);
            }
            case POKE, QUICK -> {
                damageSoldier(target, damage, soldier);
                playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.55F, style == AttackStyle.POKE ? 1.75F : 1.45F);
            }
        }

        applyAttackModifiers(soldier, target, damage);
        applyFormationAttack(soldier, target, damage, tacticalFormation);
    }

    private void wander(ArmorStand soldier, SoldierBrain brain, ClaySoldierRole role) {
        if( this.random.nextInt(role == ClaySoldierRole.SKIRMISHER ? this.settings.skirmisherWanderRoll() : this.settings.normalWanderRoll()) != 0 ) {
            return;
        }

        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        Location target = soldier.getLocation().clone().add(Math.cos(angle), 0.0D, Math.sin(angle));
        if( moveToward(soldier, brain, target, this.settings.moveStep() * speedMultiplier(soldier, this.settings.role(role)) * this.settings.wanderStepMultiplier()) ) {
            brain.movedThisTick = true;
            maybeHop(soldier, this.settings.idleJumpChance());
            brain.animation = AnimationState.RUN;
            brain.animationTicks = this.settings.tickPeriodTicks() + 3;
        }
    }

    private boolean tryDodge(ArmorStand soldier, ArmorStand attacker, ClaySoldierSettings.RoleTuning roleTuning) {
        SoldierBrain brain = brain(soldier);
        if( !this.settings.useDodging() || brain.dodgeCooldownTicks > 0 || this.random.nextDouble() > roleTuning.dodgeChance() ) {
            return false;
        }

        Vector away = horizontalDirection(attacker.getLocation(), soldier.getLocation());
        Vector side = perpendicular(away).multiply(this.random.nextBoolean() ? 1.0D : -1.0D);
        Location dodgeLocation = soldier.getLocation().clone()
                .add(side.multiply(this.settings.dodgeSideDistanceMin() + this.random.nextDouble() * Math.max(0.0D, this.settings.dodgeSideDistanceMax() - this.settings.dodgeSideDistanceMin())))
                .add(away.multiply(this.settings.dodgeBackDistance()));

        if( !moveTo(soldier, dodgeLocation) ) {
            return false;
        }

        brain.dodgeCooldownTicks = this.settings.dodgeCooldownMinTicks() + (this.settings.dodgeCooldownRange() == 0 ? 0 : this.random.nextInt(this.settings.dodgeCooldownRange()));
        brain.animation = AnimationState.DODGE;
        brain.animationTicks = 12;
        playSound(soldier.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.35F, 1.7F);
        spawnParticle(Particle.CLOUD, soldier.getLocation().clone().add(0.0D, 0.2D, 0.0D), scaledCount(3), 0.08D, 0.03D, 0.08D, 0.01D);
        return true;
    }

    private Location formationPosition(ArmorStand soldier, ArmorStand target, ClaySoldierRole role, TacticalFormation tacticalFormation) {
        if( tacticalFormation.isActive() ) {
            return tacticalFormationPosition(soldier, target, role, tacticalFormation);
        }

        Vector away = horizontalDirection(target.getLocation(), soldier.getLocation());
        Vector side = perpendicular(away);
        int slot = formationSlot(soldier, target);
        int columns = this.settings.useFormations() ? this.settings.formationColumns() : 1;
        int center = columns / 2;
        int column = slot % columns - center;
        int row = slot / columns;
        ClaySoldierSettings.RoleTuning roleTuning = this.settings.role(role);
        double distance = Math.max(0.65D, attackRange(soldier, role) * this.settings.formationBaseDistanceMultiplier()
                + roleTuning.formationDepth() + row * this.settings.formationRowSpacing());

        if( role == ClaySoldierRole.SKIRMISHER ) {
            int flankSide = brain(soldier).flankDirection == 0 ? 1 : brain(soldier).flankDirection;
            return target.getLocation().clone()
                    .add(side.clone().multiply(flankSide * (this.settings.skirmisherFlankWidth() + Math.abs(column) * this.settings.skirmisherFlankColumnSpacing())))
                    .add(away.clone().multiply(this.settings.flankBackOffset() + row * this.settings.skirmisherFlankRowSpacing()));
        }

        return target.getLocation().clone()
                .add(away.multiply(distance))
                .add(side.multiply(column * this.settings.formationLateralSpacing()));
    }

    private Location tacticalFormationPosition(ArmorStand soldier, ArmorStand target, ClaySoldierRole role, TacticalFormation tacticalFormation) {
        Vector away = horizontalDirection(target.getLocation(), soldier.getLocation());
        Vector side = perpendicular(away);
        int slot = tacticalFormationSlot(soldier, role);
        int columns = Math.max(1, this.settings.formationColumns());
        int center = columns / 2;
        int column = slot % columns - center;
        int row = slot / columns;
        double lateralSpacing = tacticalFormation == TacticalFormation.SHIELD_WALL
                ? this.settings.shieldWallLateralSpacing()
                : this.settings.formationLateralSpacing();
        double rowSpacing = tacticalFormation == TacticalFormation.SHIELD_WALL
                ? this.settings.shieldWallRowSpacing()
                : this.settings.formationRowSpacing();

        double distance = switch( tacticalFormation ) {
            case SHIELD_WALL -> this.settings.shieldWallDistance() + row * rowSpacing;
            case SPEAR_WALL -> this.settings.spearWallDistance() + row * rowSpacing;
            case COMBINED_SPEAR_WALL, SUPPORT_HIDE -> this.settings.supportBehindWallDistance() + row * rowSpacing;
            case FLANK_SUPPORT -> this.settings.shieldWallDistance() + row * rowSpacing;
            case NONE -> this.settings.attackRange();
        };

        Location base = target.getLocation().clone().add(away.multiply(distance));
        if( tacticalFormation == TacticalFormation.FLANK_SUPPORT ) {
            int flankSide = slot % 2 == 0 ? 1 : -1;
            return base.add(side.multiply(flankSide * (this.settings.flankProtectionWidth() + row * lateralSpacing)));
        }

        return base.add(side.multiply(column * lateralSpacing));
    }

    private int formationSlot(ArmorStand soldier, ArmorStand target) {
        Optional<ClayTeam> team = getTeam(soldier);
        if( team.isEmpty() ) {
            return 0;
        }

        List<ArmorStand> allies = this.activeSoldiers.stream()
                .map(this.plugin.getServer()::getEntity)
                .filter(ArmorStand.class::isInstance)
                .map(ArmorStand.class::cast)
                .filter(candidate -> !candidate.isDead() && candidate.getWorld().equals(soldier.getWorld()))
                .filter(candidate -> getTeam(candidate).orElse(null) == team.get())
                .filter(candidate -> candidate.getLocation().distanceSquared(target.getLocation()) <= formationRangeSquared())
                .sorted(Comparator.<ArmorStand>comparingInt(candidate -> getRole(candidate).ordinal()).thenComparing(Entity::getUniqueId))
                .toList();

        int index = allies.indexOf(soldier);
        return Math.max(0, index);
    }

    private Optional<Location> formationCentroid(ArmorStand soldier, ClaySoldierRole role) {
        Optional<ClayTeam> team = getTeam(soldier);
        if( team.isEmpty() ) {
            return Optional.empty();
        }

        double radiusSquared = this.settings.tacticalFormationScanRange() * this.settings.tacticalFormationScanRange();
        List<ArmorStand> allies = this.activeSoldiers.stream()
                .map(this.plugin.getServer()::getEntity)
                .filter(ArmorStand.class::isInstance)
                .map(ArmorStand.class::cast)
                .filter(candidate -> !candidate.isDead() && candidate.getWorld().equals(soldier.getWorld()))
                .filter(candidate -> getTeam(candidate).orElse(null) == team.get())
                .filter(candidate -> getRole(candidate) == role)
                .filter(candidate -> candidate.getLocation().distanceSquared(soldier.getLocation()) <= radiusSquared)
                .toList();

        if( allies.isEmpty() ) {
            return Optional.empty();
        }

        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for( ArmorStand ally : allies ) {
            Location location = ally.getLocation();
            x += location.getX();
            y += location.getY();
            z += location.getZ();
        }

        return Optional.of(new Location(soldier.getWorld(), x / allies.size(), y / allies.size(), z / allies.size()));
    }

    private TacticalFormation tacticalFormation(ArmorStand soldier, ClaySoldierRole role) {
        if( !this.settings.useFormations() || !this.settings.useTacticalFormations() ) {
            return TacticalFormation.NONE;
        }

        Optional<ClayTeam> team = getTeam(soldier);
        if( team.isEmpty() ) {
            return TacticalFormation.NONE;
        }

        boolean shieldWall = roleCountNear(soldier, team.get(), ClaySoldierRole.GUARD) >= this.settings.tacticalFormationMinSoldiers();
        boolean spearWall = roleCountNear(soldier, team.get(), ClaySoldierRole.SPEARMAN) >= this.settings.tacticalFormationMinSoldiers();

        if( role == ClaySoldierRole.GUARD && shieldWall ) {
            return TacticalFormation.SHIELD_WALL;
        }

        if( role == ClaySoldierRole.SPEARMAN && spearWall ) {
            if( shieldWall && stableChance(soldier, this.settings.combinedWallChance()) ) {
                return TacticalFormation.COMBINED_SPEAR_WALL;
            }
            return TacticalFormation.SPEAR_WALL;
        }

        if( role == ClaySoldierRole.SLINGER && shieldWall ) {
            return TacticalFormation.SUPPORT_HIDE;
        }

        if( role != ClaySoldierRole.SLINGER && (shieldWall || spearWall) ) {
            return TacticalFormation.FLANK_SUPPORT;
        }

        return TacticalFormation.NONE;
    }

    private int roleCountNear(ArmorStand soldier, ClayTeam team, ClaySoldierRole role) {
        double radiusSquared = this.settings.tacticalFormationScanRange() * this.settings.tacticalFormationScanRange();
        int count = 0;
        for( UUID id : this.activeSoldiers ) {
            Entity entity = this.plugin.getServer().getEntity(id);
            if( !(entity instanceof ArmorStand candidate) || candidate.isDead() || !candidate.getWorld().equals(soldier.getWorld()) ) {
                continue;
            }

            if( getTeam(candidate).orElse(null) == team && getRole(candidate) == role
                    && candidate.getLocation().distanceSquared(soldier.getLocation()) <= radiusSquared ) {
                count++;
            }
        }

        return count;
    }

    private int tacticalFormationSlot(ArmorStand soldier, ClaySoldierRole role) {
        Optional<ClayTeam> team = getTeam(soldier);
        if( team.isEmpty() ) {
            return 0;
        }

        double radiusSquared = this.settings.tacticalFormationScanRange() * this.settings.tacticalFormationScanRange();
        List<ArmorStand> allies = this.activeSoldiers.stream()
                .map(this.plugin.getServer()::getEntity)
                .filter(ArmorStand.class::isInstance)
                .map(ArmorStand.class::cast)
                .filter(candidate -> !candidate.isDead() && candidate.getWorld().equals(soldier.getWorld()))
                .filter(candidate -> getTeam(candidate).orElse(null) == team.get())
                .filter(candidate -> getRole(candidate) == role)
                .filter(candidate -> candidate.getLocation().distanceSquared(soldier.getLocation()) <= radiusSquared)
                .sorted(Comparator.comparing(Entity::getUniqueId))
                .toList();

        int index = allies.indexOf(soldier);
        return Math.max(0, index);
    }

    private boolean stableChance(ArmorStand soldier, double chance) {
        if( chance <= 0.0D ) {
            return false;
        }
        if( chance >= 1.0D ) {
            return true;
        }

        long value = Math.abs(soldier.getUniqueId().getLeastSignificantBits() % 10_000L);
        return value / 10_000.0D < chance;
    }

    private Location flankPosition(ArmorStand soldier, ArmorStand target, SoldierBrain brain) {
        Vector away = horizontalDirection(target.getLocation(), soldier.getLocation());
        Vector side = perpendicular(away).multiply(brain.flankDirection);
        return target.getLocation().clone()
                .add(side.multiply(this.settings.flankSideOffset() + this.random.nextDouble() * this.settings.flankSideJitter()))
                .add(away.multiply(this.settings.flankBackOffset()));
    }

    private void leapToward(ArmorStand soldier, ArmorStand target) {
        Vector direction = horizontalDirection(soldier.getLocation(), target.getLocation());
        Location leapLocation = soldier.getLocation().clone().add(direction.multiply(this.settings.jumpForwardDistance())).add(0.0D, this.settings.jumpHeight(), 0.0D);
        if( moveTo(soldier, leapLocation) ) {
            soldier.setVelocity(new Vector(0.0D, this.settings.jumpVelocityY(), 0.0D));
            spawnParticle(Particle.CLOUD, soldier.getLocation(), scaledCount(3), 0.07D, 0.02D, 0.07D, 0.01D);
            playSound(soldier.getLocation(), Sound.ENTITY_SLIME_JUMP_SMALL, 0.45F, 1.45F);
        }
    }

    private void pushAway(ArmorStand target, Location source, double amount) {
        Vector away = horizontalDirection(source, target.getLocation());
        moveTo(target, target.getLocation().clone().add(away.multiply(amount)));
    }

    private void drawSlingTrail(ArmorStand soldier, ArmorStand target) {
        Location start = soldier.getLocation().clone().add(0.0D, 0.72D, 0.0D);
        Location end = target.getLocation().clone().add(0.0D, 0.55D, 0.0D);
        Vector delta = end.toVector().subtract(start.toVector());
        int points = Math.max(4, (int) Math.ceil(delta.length() * 3.0D));
        Vector step = delta.multiply(1.0D / points);

        for( int i = 0; i <= points; i++ ) {
            spawnParticle(Particle.ITEM, start.clone().add(step.clone().multiply(i)), scaledCount(1), 0.01D, 0.01D, 0.01D, 0.0D,
                    new ItemStack(Material.CLAY_BALL));
        }
    }

    private void applyAttackModifiers(ArmorStand soldier, ArmorStand target, double baseDamage) {
        Set<ClaySoldierModifier> modifiers = getModifiers(soldier);
        if( modifiers.isEmpty() ) {
            return;
        }

        if( modifiers.contains(ClaySoldierModifier.EXPLOSIVE) ) {
            ClaySoldierSettings.ModifierTuning tuning = this.settings.modifier(ClaySoldierModifier.EXPLOSIVE);
            if( tuning.splashRadius() > 0.0D && tuning.splashDamageMultiplier() > 0.0D ) {
                ClayTeam friendlyTeam = getTeam(soldier).orElse(ClayTeam.CLAY);
                for( ArmorStand enemy : nearbyEnemySoldiers(target, friendlyTeam, tuning.splashRadius()) ) {
                    if( !enemy.equals(target) ) {
                        damageSoldier(enemy, baseDamage * tuning.splashDamageMultiplier(), soldier);
                    }
                }
                spawnParticle(Particle.EXPLOSION, target.getLocation().clone().add(0.0D, 0.45D, 0.0D), scaledCount(1));
                playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35F, 1.8F);
            }
        }

        if( modifiers.contains(ClaySoldierModifier.VENOM) && !target.isDead() && isSoldier(target) ) {
            double venomDamage = this.settings.modifier(ClaySoldierModifier.VENOM).venomDamage();
            if( venomDamage > 0.0D ) {
                damageSoldier(target, venomDamage, soldier);
                spawnParticle(Particle.DUST, target.getLocation().clone().add(0.0D, 0.55D, 0.0D), scaledCount(4), 0.08D, 0.08D, 0.08D,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(0x2E7D32), 0.7F));
            }
        }

        if( modifiers.contains(ClaySoldierModifier.LIFESTEAL) ) {
            double lifesteal = this.settings.modifier(ClaySoldierModifier.LIFESTEAL).lifestealAmount();
            if( lifesteal > 0.0D ) {
                healSoldier(soldier, lifesteal);
                spawnParticle(Particle.HEART, soldier.getLocation().clone().add(0.0D, 0.75D, 0.0D), scaledCount(1), 0.04D, 0.04D, 0.04D, 0.0D);
            }
        }
    }

    private void applyFormationAttack(ArmorStand soldier, ArmorStand target, double baseDamage, TacticalFormation tacticalFormation) {
        if( !tacticalFormation.isWall() || target.isDead() || !isSoldier(target) ) {
            return;
        }

        double bonusDamage = Math.max(0.0D, baseDamage * (this.settings.specialFormationDamageMultiplier() - 1.0D));
        switch( tacticalFormation ) {
            case SHIELD_WALL -> {
                if( bonusDamage > 0.0D ) {
                    damageSoldier(target, bonusDamage, soldier);
                }
                pushAway(target, soldier.getLocation(), this.settings.shieldBashPushDistance() * 0.75D);
                spawnParticle(Particle.CRIT, target.getLocation().clone().add(0.0D, 0.55D, 0.0D), scaledCount(3), 0.08D, 0.08D, 0.08D, 0.0D);
                playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.55F, 0.85F);
            }
            case SPEAR_WALL, COMBINED_SPEAR_WALL -> {
                if( bonusDamage > 0.0D ) {
                    damageSoldier(target, bonusDamage, soldier);
                }
                ClayTeam friendlyTeam = getTeam(soldier).orElse(ClayTeam.CLAY);
                for( ArmorStand enemy : nearbyEnemySoldiers(target, friendlyTeam, this.settings.sweepClusterRadius()) ) {
                    if( !enemy.equals(target) ) {
                        damageSoldier(enemy, baseDamage * 0.35D, soldier);
                    }
                }
                spawnParticle(Particle.CRIT, target.getLocation().clone().add(0.0D, 0.55D, 0.0D), scaledCount(5), 0.12D, 0.06D, 0.12D, 0.0D);
                playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6F, tacticalFormation == TacticalFormation.COMBINED_SPEAR_WALL ? 1.0F : 1.25F);
            }
            case NONE, SUPPORT_HIDE, FLANK_SUPPORT -> {
                // Not a wall attack.
            }
        }
    }

    private void healSoldier(ArmorStand soldier, double amount) {
        if( amount <= 0.0D || !isSoldier(soldier) || soldier.isDead() ) {
            return;
        }

        double health = Math.min(maxHealth(soldier), getHealth(soldier) + amount);
        soldier.getPersistentDataContainer().set(this.healthKey, PersistentDataType.DOUBLE, health);
        updateNameplate(soldier);
    }

    private int nearbyEnemies(ArmorStand center, ClayTeam friendlyTeam, double radius) {
        return nearbyEnemySoldiers(center, friendlyTeam, radius).size();
    }

    private List<ArmorStand> nearbyEnemySoldiers(ArmorStand center, ClayTeam friendlyTeam, double radius) {
        double radiusSquared = radius * radius;
        return this.activeSoldiers.stream()
                .map(this.plugin.getServer()::getEntity)
                .filter(ArmorStand.class::isInstance)
                .map(ArmorStand.class::cast)
                .filter(candidate -> !candidate.isDead() && candidate.getWorld().equals(center.getWorld()))
                .filter(candidate -> !candidate.equals(center))
                .filter(candidate -> getTeam(candidate).orElse(friendlyTeam) != friendlyTeam)
                .filter(candidate -> candidate.getLocation().distanceSquared(center.getLocation()) <= radiusSquared)
                .toList();
    }

    private boolean moveToward(ArmorStand soldier, SoldierBrain brain, Location target, double stepSize) {
        Location current = soldier.getLocation();
        Vector direction = target.toVector().subtract(current.toVector());
        direction.setY(0.0D);

        if( direction.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED ) {
            return false;
        }

        direction.normalize();
        Location next = current.clone().add(direction.clone().multiply(stepSize));
        next.setDirection(direction);
        boolean directRouteClear = hasClearMovementPath(current, target);
        if( directRouteClear && moveTo(soldier, next) ) {
            brain.clearPath();
            return true;
        }

        if( !this.settings.pathfindingEnabled() ) {
            return false;
        }

        if( this.settings.pathfindingPlannerEnabled() && followPlannedPath(soldier, brain, target, stepSize) ) {
            return true;
        }

        brain.clearPath();
        return tryBlockedMovement(soldier, current, direction, stepSize);
    }

    private boolean followPlannedPath(ArmorStand soldier, SoldierBrain brain, Location target, double stepSize) {
        if( needsNewPath(brain, target) ) {
            Optional<List<Location>> plannedPath = planPath(soldier.getLocation(), target);
            if( plannedPath.isEmpty() || plannedPath.get().isEmpty() ) {
                brain.clearPath();
                return false;
            }

            brain.path = plannedPath.get();
            brain.pathIndex = 0;
            brain.pathTarget = target.clone();
            brain.pathRefreshTicks = this.settings.pathfindingRouteRefreshTicks();
        }

        Location current = soldier.getLocation();
        advanceReachedWaypoints(brain, current);
        if( brain.pathIndex >= brain.path.size() ) {
            brain.clearPath();
            return false;
        }

        if( this.settings.pathfindingSmoothRoutes() ) {
            skipVisibleWaypoints(brain, current);
        }

        Location waypoint = brain.path.get(brain.pathIndex);
        Vector direction = waypoint.toVector().subtract(current.toVector());
        direction.setY(0.0D);
        if( direction.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED ) {
            brain.pathIndex++;
            return brain.pathIndex < brain.path.size();
        }

        direction.normalize();
        double distance = current.distance(waypoint);
        Location next = current.clone().add(direction.clone().multiply(Math.min(stepSize, distance)));
        next.setY(current.getY() + Math.max(-this.settings.pathfindingMaxDropHeight(), Math.min(this.settings.pathfindingMaxStepHeight(), waypoint.getY() - current.getY())));
        next.setDirection(direction);

        if( !hasClearMovementPath(current, next, true) || !moveTo(soldier, next) ) {
            brain.clearPath();
            return false;
        }

        return true;
    }

    private boolean needsNewPath(SoldierBrain brain, Location target) {
        if( brain.path.isEmpty() || brain.pathIndex >= brain.path.size() || brain.pathTarget == null ) {
            return true;
        }
        if( brain.pathRefreshTicks <= 0 ) {
            return true;
        }
        if( !brain.pathTarget.getWorld().equals(target.getWorld()) ) {
            return true;
        }

        double threshold = this.settings.pathfindingRouteTargetMoveThreshold();
        return brain.pathTarget.distanceSquared(target) > threshold * threshold;
    }

    private void advanceReachedWaypoints(SoldierBrain brain, Location current) {
        double reachDistanceSquared = this.settings.pathfindingWaypointReachDistance() * this.settings.pathfindingWaypointReachDistance();
        while( brain.pathIndex < brain.path.size() && current.distanceSquared(brain.path.get(brain.pathIndex)) <= reachDistanceSquared ) {
            brain.pathIndex++;
        }
    }

    private void skipVisibleWaypoints(SoldierBrain brain, Location current) {
        for( int index = brain.path.size() - 1; index > brain.pathIndex; index-- ) {
            if( hasClearMovementPath(current, brain.path.get(index)) ) {
                brain.pathIndex = index;
                return;
            }
        }
    }

    private Optional<List<Location>> planPath(Location start, Location target) {
        if( !start.getWorld().equals(target.getWorld()) ) {
            return Optional.empty();
        }

        double maxRange = this.settings.pathfindingMaxRange();
        Location plannedTarget = clampPathTarget(start, target, maxRange);
        World world = start.getWorld();
        Optional<PathNode> startNode = nearestPathNode(start, start.getY(), 1);
        if( startNode.isEmpty() ) {
            return Optional.empty();
        }

        Optional<PathNode> goalNode = nearestPathNode(plannedTarget, startNode.get().y(), 3);
        if( goalNode.isEmpty() ) {
            return Optional.empty();
        }

        if( startNode.get().equals(goalNode.get()) ) {
            return Optional.empty();
        }

        PriorityQueue<PathSearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.fCost));
        Map<PathNode, PathSearchNode> allNodes = new HashMap<>();
        Set<PathNode> closed = new HashSet<>();
        PathSearchNode first = new PathSearchNode(startNode.get(), null, 0.0D, pathHeuristic(startNode.get(), goalNode.get()));
        open.add(first);
        allNodes.put(first.node, first);

        int visited = 0;
        while( !open.isEmpty() && visited++ < this.settings.pathfindingMaxNodes() ) {
            PathSearchNode current = open.poll();
            if( !closed.add(current.node) ) {
                continue;
            }

            if( current.node.equals(goalNode.get()) ) {
                return Optional.of(pathLocations(world, current));
            }

            for( PathNode neighbor : pathNeighbors(world, current.node) ) {
                if( closed.contains(neighbor) || exceedsPathRange(startNode.get(), goalNode.get(), neighbor, maxRange) ) {
                    continue;
                }

                double cost = current.gCost + pathMoveCost(world, current.node, neighbor);
                PathSearchNode known = allNodes.get(neighbor);
                if( known != null && cost >= known.gCost ) {
                    continue;
                }

                PathSearchNode next = new PathSearchNode(neighbor, current, cost, cost + pathHeuristic(neighbor, goalNode.get()));
                allNodes.put(neighbor, next);
                open.add(next);
            }
        }

        return Optional.empty();
    }

    private Location clampPathTarget(Location start, Location target, double maxRange) {
        Vector delta = target.toVector().subtract(start.toVector());
        delta.setY(0.0D);
        if( delta.length() <= maxRange ) {
            return target;
        }

        Vector direction = delta.normalize().multiply(maxRange);
        return start.clone().add(direction);
    }

    private Optional<PathNode> nearestPathNode(Location location, double referenceY, int radius) {
        World world = location.getWorld();
        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();
        for( int currentRadius = 0; currentRadius <= radius; currentRadius++ ) {
            Optional<PathNode> closest = Optional.empty();
            double closestDistanceSquared = Double.MAX_VALUE;
            for( int dx = -currentRadius; dx <= currentRadius; dx++ ) {
                for( int dz = -currentRadius; dz <= currentRadius; dz++ ) {
                    if( Math.max(Math.abs(dx), Math.abs(dz)) != currentRadius ) {
                        continue;
                    }

                    Optional<PathNode> node = walkablePathNodeAt(world, centerX + dx, centerZ + dz, referenceY);
                    if( node.isEmpty() ) {
                        continue;
                    }

                    double distanceSquared = nodeLocation(world, node.get()).distanceSquared(location);
                    if( distanceSquared < closestDistanceSquared ) {
                        closest = node;
                        closestDistanceSquared = distanceSquared;
                    }
                }
            }

            if( closest.isPresent() ) {
                return closest;
            }
        }

        return Optional.empty();
    }

    private List<PathNode> pathNeighbors(World world, PathNode node) {
        int[][] directions = this.settings.pathfindingAllowDiagonal()
                ? new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}
                : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<PathNode> neighbors = new ArrayList<>(directions.length);
        double currentY = node.y();

        for( int[] direction : directions ) {
            int dx = direction[0];
            int dz = direction[1];
            Optional<PathNode> neighbor = walkablePathNodeAt(world, node.x + dx, node.z + dz, currentY);
            if( neighbor.isEmpty() || !canTraverseHeight(currentY, neighbor.get().y()) ) {
                continue;
            }

            if( dx != 0 && dz != 0 && this.settings.pathfindingAvoidCornerCutting()
                    && (!walkablePathNodeAt(world, node.x + dx, node.z, currentY).filter(candidate -> canTraverseHeight(currentY, candidate.y())).isPresent()
                    || !walkablePathNodeAt(world, node.x, node.z + dz, currentY).filter(candidate -> canTraverseHeight(currentY, candidate.y())).isPresent()) ) {
                continue;
            }

            if( !hasClearMovementPath(nodeLocation(world, node), nodeLocation(world, neighbor.get()), true) ) {
                continue;
            }

            neighbors.add(neighbor.get());
        }

        return neighbors;
    }

    private Optional<PathNode> walkablePathNodeAt(World world, int x, int z, double referenceY) {
        int referenceHalf = (int) Math.round(referenceY * 2.0D);
        int maxUp = Math.max(0, (int) Math.ceil(this.settings.pathfindingMaxStepHeight() * 2.0D));
        int maxDown = Math.max(0, (int) Math.ceil(this.settings.pathfindingMaxDropHeight() * 2.0D));

        for( int offset : orderedHeightOffsets(maxUp, maxDown) ) {
            int yHalf = referenceHalf + offset;
            double y = yHalf / 2.0D;
            if( y < world.getMinHeight() || y > world.getMaxHeight() - 2 ) {
                continue;
            }

            PathNode node = new PathNode(x, yHalf, z);
            Location location = nodeLocation(world, node);
            if( isPathWalkableLocation(location) ) {
                return Optional.of(node);
            }
        }

        return Optional.empty();
    }

    private List<Integer> orderedHeightOffsets(int maxUp, int maxDown) {
        List<Integer> offsets = new ArrayList<>(maxUp + maxDown + 1);
        offsets.add(0);
        int max = Math.max(maxUp, maxDown);
        for( int step = 1; step <= max; step++ ) {
            if( step <= maxUp ) {
                offsets.add(step);
            }
            if( step <= maxDown ) {
                offsets.add(-step);
            }
        }

        return offsets;
    }

    private boolean canTraverseHeight(double fromY, double toY) {
        return toY - fromY <= this.settings.pathfindingMaxStepHeight() + 0.01D
                && fromY - toY <= this.settings.pathfindingMaxDropHeight() + 0.01D;
    }

    private boolean exceedsPathRange(PathNode start, PathNode goal, PathNode candidate, double maxRange) {
        int margin = this.settings.pathfindingSearchMargin();
        int minX = Math.min(start.x, goal.x) - margin;
        int maxX = Math.max(start.x, goal.x) + margin;
        int minZ = Math.min(start.z, goal.z) - margin;
        int maxZ = Math.max(start.z, goal.z) + margin;
        if( candidate.x < minX || candidate.x > maxX || candidate.z < minZ || candidate.z > maxZ ) {
            return true;
        }

        double dx = candidate.x - start.x;
        double dz = candidate.z - start.z;
        return dx * dx + dz * dz > maxRange * maxRange;
    }

    private double pathMoveCost(World world, PathNode from, PathNode to) {
        double dx = Math.abs(to.x - from.x);
        double dz = Math.abs(to.z - from.z);
        double baseCost = dx != 0.0D && dz != 0.0D ? Math.sqrt(2.0D) : 1.0D;
        double verticalCost = Math.abs(to.y() - from.y()) * this.settings.pathfindingVerticalPenalty();
        return baseCost + verticalCost + pathHazardCost(world, to);
    }

    private double pathHeuristic(PathNode from, PathNode to) {
        double dx = Math.abs(from.x - to.x);
        double dz = Math.abs(from.z - to.z);
        double diagonal = Math.min(dx, dz);
        double straight = Math.max(dx, dz) - diagonal;
        double y = Math.abs(from.y() - to.y()) * this.settings.pathfindingVerticalPenalty();
        return (diagonal * Math.sqrt(2.0D) + straight + y) * this.settings.pathfindingHeuristicWeight();
    }

    private List<Location> pathLocations(World world, PathSearchNode endNode) {
        List<Location> locations = new ArrayList<>();
        PathSearchNode cursor = endNode;
        while( cursor != null ) {
            locations.add(nodeLocation(world, cursor.node));
            cursor = cursor.parent;
        }

        Collections.reverse(locations);
        if( !locations.isEmpty() ) {
            locations.remove(0);
        }

        return locations;
    }

    private Location nodeLocation(World world, PathNode node) {
        return new Location(world, node.x + 0.5D, node.y(), node.z + 0.5D);
    }

    private boolean isPathWalkableLocation(Location location) {
        return canOccupy(location) && hasStandingSurface(location);
    }

    private boolean hasStandingSurface(Location location) {
        Block feet = location.getBlock();
        if( isPartialStepBlock(feet) && location.getY() + 0.02D >= blockSurfaceY(feet) && location.getY() < feet.getY() + 1.0D ) {
            return true;
        }

        Block below = location.clone().subtract(0.0D, 0.05D, 0.0D).getBlock();
        return !below.isPassable() || isPartialStepBlock(below);
    }

    private double pathHazardCost(World world, PathNode node) {
        if( !this.settings.pathfindingAvoidHazards() ) {
            return 0.0D;
        }

        return hasPathHazard(nodeLocation(world, node)) ? this.settings.pathfindingHazardPenalty() : 0.0D;
    }

    private boolean hasPathHazard(Location location) {
        Block feet = location.getBlock();
        Block below = location.clone().subtract(0.0D, 0.05D, 0.0D).getBlock();
        Block head = location.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        return isHazard(feet.getType()) || isHazard(below.getType()) || isHazard(head.getType());
    }

    private boolean isHazard(Material material) {
        return switch( material ) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE -> true;
            default -> false;
        };
    }

    private boolean moveTo(ArmorStand soldier, Location location) {
        Optional<Location> resolved = resolveOccupyLocation(soldier.getLocation(), location);
        if( resolved.isPresent() ) {
            soldier.teleport(resolved.get());
            return true;
        }

        return false;
    }

    private boolean tryBlockedMovement(ArmorStand soldier, Location current, Vector direction, double stepSize) {
        int side = this.random.nextBoolean() ? 1 : -1;
        double sideStep = Math.max(stepSize * this.settings.pathfindingSideStepMultiplier(), this.settings.pathfindingDetourProbeDistance());
        double backStep = Math.max(stepSize * this.settings.pathfindingBackStepMultiplier(), this.settings.pathfindingDetourProbeDistance() * 0.65D);

        for( double degrees : new double[] {25.0D, 45.0D, 70.0D, 95.0D, 130.0D} ) {
            if( tryStep(soldier, current, rotateY(direction, degrees * side), sideStep) ) {
                return true;
            }

            if( tryStep(soldier, current, rotateY(direction, -degrees * side), sideStep) ) {
                return true;
            }
        }

        Vector reverse = direction.clone().multiply(-1.0D);
        if( tryStep(soldier, current, reverse, backStep) ) {
            return true;
        }

        faceDirection(soldier, reverse);
        return false;
    }

    private boolean tryStep(ArmorStand soldier, Location current, Vector direction, double stepSize) {
        Location next = current.clone().add(direction.clone().multiply(stepSize));
        next.setDirection(direction);
        return hasClearMovementPath(current, next) && moveTo(soldier, next);
    }

    private Vector rotateY(Vector direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = direction.getX() * cos - direction.getZ() * sin;
        double z = direction.getX() * sin + direction.getZ() * cos;
        return new Vector(x, 0.0D, z).normalize();
    }

    private boolean canOccupy(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        return canOccupyFeet(feet, location) && head.isPassable();
    }

    private boolean canOccupyFeet(Block feet, Location location) {
        if( feet.isPassable() ) {
            return true;
        }

        return isPartialStepBlock(feet)
                && location.getY() + 0.02D >= blockSurfaceY(feet)
                && location.getY() < feet.getY() + 1.0D;
    }

    private Optional<Location> resolveOccupyLocation(Location current, Location target) {
        if( canOccupy(target) ) {
            return Optional.of(target);
        }

        Optional<Location> stepped = resolveStepLocation(current, target);
        if( stepped.isPresent() ) {
            return stepped;
        }

        int maxStepHeight = Math.max(0, (int) Math.floor(this.settings.pathfindingMaxStepHeight()));
        for( int stepHeight = 1; stepHeight <= maxStepHeight; stepHeight++ ) {
            Location steppedLocation = target.clone().add(0.0D, stepHeight, 0.0D);
            if( canOccupy(steppedLocation) ) {
                if( this.settings.useJumping() ) {
                    spawnParticle(Particle.CLOUD, steppedLocation, scaledCount(1), 0.04D, 0.02D, 0.04D, 0.004D);
                }
                return Optional.of(steppedLocation);
            }
        }

        return Optional.empty();
    }

    private Optional<Location> resolveStepLocation(Location current, Location target) {
        if( !this.settings.pathfindingEnabled() ) {
            return Optional.empty();
        }

        Block obstacle = target.getBlock();
        if( obstacle.isPassable() ) {
            Vector direction = target.toVector().subtract(current.toVector());
            direction.setY(0.0D);
            if( direction.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED ) {
                return Optional.empty();
            }

            direction.normalize();
            obstacle = current.clone()
                    .add(direction.multiply(Math.min(this.settings.pathfindingDetourProbeDistance(), Math.max(this.settings.moveStep(), current.distance(target) + 0.10D))))
                    .getBlock();
        }

        if( !isStepObstacle(obstacle) || !obstacle.getRelative(0, 1, 0).isPassable() ) {
            return Optional.empty();
        }

        Location stepped = target.clone();
        stepped.setY(blockSurfaceY(obstacle));
        return canOccupy(stepped) ? Optional.of(stepped) : Optional.empty();
    }

    private boolean hasClearMovementPath(Location from, Location to) {
        return hasClearMovementPath(from, to, false);
    }

    private boolean hasClearMovementPath(Location from, Location to, boolean allowHazards) {
        if( !this.settings.pathfindingEnabled() ) {
            return true;
        }

        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();
        if( distance < 0.001D ) {
            return true;
        }

        int samples = Math.max(1, (int) Math.ceil(distance / Math.max(0.05D, this.settings.pathfindingObstacleProbeDistance())));
        Vector step = delta.multiply(1.0D / samples);
        for( int i = 1; i <= samples; i++ ) {
            Location sample = from.clone().add(step.clone().multiply(i));
            if( !allowHazards && this.settings.pathfindingAvoidHazards() && hasPathHazard(sample) ) {
                return false;
            }
            if( canOccupy(sample) || resolveStepLocation(from, sample).isPresent() ) {
                continue;
            }

            return false;
        }

        return true;
    }

    private boolean hasAttackLineOfSight(ArmorStand soldier, ArmorStand target) {
        if( !this.settings.requireLineOfSightForAttacks() ) {
            return true;
        }

        Location start = soldier.getLocation().clone().add(0.0D, 0.58D, 0.0D);
        Location end = target.getLocation().clone().add(0.0D, 0.58D, 0.0D);
        Vector delta = end.toVector().subtract(start.toVector());
        double distance = delta.length();
        if( distance < 0.001D ) {
            return true;
        }

        int samples = Math.max(1, (int) Math.ceil(distance / Math.max(0.05D, this.settings.pathfindingLineOfSightProbeDistance())));
        Vector step = delta.multiply(1.0D / samples);
        for( int i = 1; i < samples; i++ ) {
            Location sample = start.clone().add(step.clone().multiply(i));
            if( blocksAttackLineOfSight(sample) ) {
                return false;
            }
        }

        return true;
    }

    private boolean blocksAttackLineOfSight(Location sample) {
        Block block = sample.getBlock();
        if( block.isPassable() ) {
            return false;
        }

        return !isPartialStepBlock(block) || sample.getY() <= blockSurfaceY(block) + 0.05D;
    }

    private boolean isStepObstacle(Block block) {
        if( isSlab(block) ) {
            return this.settings.pathfindingAllowSlabs();
        }
        if( isStairs(block) ) {
            return this.settings.pathfindingAllowStairs();
        }

        return this.settings.pathfindingAllowOneBlockStep()
                && block.getType().isSolid()
                && block.getType().isOccluding();
    }

    private boolean isPartialStepBlock(Block block) {
        return (this.settings.pathfindingAllowSlabs() && isSlab(block))
                || (this.settings.pathfindingAllowStairs() && isStairs(block));
    }

    private boolean isSlab(Block block) {
        return block.getBlockData() instanceof Slab;
    }

    private boolean isStairs(Block block) {
        return block.getType().name().endsWith("_STAIRS");
    }

    private double blockSurfaceY(Block block) {
        if( block.getBlockData() instanceof Slab slab && slab.getType() == Slab.Type.BOTTOM ) {
            return block.getY() + 0.5D;
        }

        return block.getY() + 1.0D;
    }

    private void face(ArmorStand soldier, Location target) {
        Location current = soldier.getLocation();
        Vector direction = target.toVector().subtract(current.toVector());
        direction.setY(0.0D);
        if( direction.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED ) {
            return;
        }

        current.setDirection(direction);
        soldier.teleport(current);
    }

    private void faceDirection(ArmorStand soldier, Vector direction) {
        if( direction.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED ) {
            return;
        }

        Location current = soldier.getLocation();
        current.setDirection(direction);
        soldier.teleport(current);
    }

    private double attackRange(ArmorStand soldier, ClaySoldierRole role) {
        return this.settings.attackRange() * rangeMultiplier(soldier, this.settings.role(role));
    }

    private double healthMultiplier(ArmorStand soldier, ClaySoldierSettings.RoleTuning roleTuning) {
        return roleTuning.healthMultiplier() + getModifiers(soldier).stream()
                .map(this.settings::modifier)
                .mapToDouble(ClaySoldierSettings.ModifierTuning::healthMultiplierBonus)
                .sum();
    }

    private double damageMultiplier(ArmorStand soldier, ClaySoldierSettings.RoleTuning roleTuning) {
        return roleTuning.damageMultiplier() + getModifiers(soldier).stream()
                .map(this.settings::modifier)
                .mapToDouble(ClaySoldierSettings.ModifierTuning::damageMultiplierBonus)
                .sum();
    }

    private double speedMultiplier(ArmorStand soldier, ClaySoldierSettings.RoleTuning roleTuning) {
        return roleTuning.speedMultiplier() + getModifiers(soldier).stream()
                .map(this.settings::modifier)
                .mapToDouble(ClaySoldierSettings.ModifierTuning::speedMultiplierBonus)
                .sum();
    }

    private double rangeMultiplier(ArmorStand soldier, ClaySoldierSettings.RoleTuning roleTuning) {
        return roleTuning.rangeMultiplier() + getModifiers(soldier).stream()
                .map(this.settings::modifier)
                .mapToDouble(ClaySoldierSettings.ModifierTuning::rangeMultiplierBonus)
                .sum();
    }

    private double cooldownMultiplier(ArmorStand soldier) {
        return getModifiers(soldier).stream()
                .map(this.settings::modifier)
                .mapToDouble(ClaySoldierSettings.ModifierTuning::cooldownMultiplier)
                .filter(value -> value > 0.0D)
                .reduce(1.0D, (left, right) -> left * right);
    }

    private Vector horizontalDirection(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        direction.setY(0.0D);
        if( direction.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED ) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            direction = new Vector(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        return direction.normalize();
    }

    private Vector perpendicular(Vector direction) {
        return new Vector(-direction.getZ(), 0.0D, direction.getX()).normalize();
    }

    private double getHealth(ArmorStand soldier) {
        Double health = soldier.getPersistentDataContainer().get(this.healthKey, PersistentDataType.DOUBLE);
        return health == null ? maxHealth(soldier) : health;
    }

    private double maxHealth(ArmorStand soldier) {
        return maxHealth(getRole(soldier), getModifiers(soldier));
    }

    private double maxHealth(ClaySoldierRole role, Set<ClaySoldierModifier> modifiers) {
        double modifierBonus = modifiers.stream()
                .map(this.settings::modifier)
                .mapToDouble(ClaySoldierSettings.ModifierTuning::healthMultiplierBonus)
                .sum();
        return this.settings.maxHealth() * (this.settings.role(role).healthMultiplier() + modifierBonus);
    }

    private Set<ClaySoldierModifier> getModifiers(Entity entity) {
        if( !isSoldier(entity) ) {
            return Set.of();
        }

        String serialized = entity.getPersistentDataContainer().get(this.modifiersKey, PersistentDataType.STRING);
        if( serialized == null || serialized.isBlank() ) {
            return Set.of();
        }

        Set<ClaySoldierModifier> modifiers = new HashSet<>();
        for( String value : serialized.split(",") ) {
            ClaySoldierModifier.fromKey(value.trim()).ifPresent(modifiers::add);
        }

        return Set.copyOf(modifiers);
    }

    private String serializeModifiers(Set<ClaySoldierModifier> modifiers) {
        return modifiers.stream()
                .map(ClaySoldierModifier::key)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private void updateNameplate(ArmorStand soldier) {
        if( !this.settings.useNameplates() ) {
            soldier.setCustomNameVisible(false);
            return;
        }

        Optional<ClayTeam> team = getTeam(soldier);
        ClaySoldierRole role = getRole(soldier);
        double maxHealth = Math.max(1.0D, maxHealth(soldier));
        double health = Math.max(0.0D, Math.min(maxHealth, getHealth(soldier)));

        Component nameplate = Component.empty();
        boolean hasContent = false;

        if( this.settings.nameplateShowTeam() && team.isPresent() ) {
            nameplate = nameplate.append(Component.text(team.get().displayName(), team.get().textColor()));
            hasContent = true;
        }

        if( this.settings.nameplateShowRole() ) {
            nameplate = appendSpaceIfNeeded(nameplate, hasContent)
                    .append(Component.text(role.displayName(), role.textColor()));
            hasContent = true;
        }

        if( brain(soldier).activeFormation.isActive() && !this.settings.nameplateFormationIndicator().isBlank() ) {
            nameplate = appendSpaceIfNeeded(nameplate, hasContent)
                    .append(Component.text(this.settings.nameplateFormationIndicator(), NamedTextColor.GOLD));
            hasContent = true;
        }

        nameplate = appendSpaceIfNeeded(nameplate, hasContent)
                .append(healthBar(health, maxHealth));
        hasContent = true;

        if( this.settings.nameplateShowHealthText() ) {
            nameplate = appendSpaceIfNeeded(nameplate, hasContent)
                    .append(Component.text(formatHealth(health) + "/" + formatHealth(maxHealth), NamedTextColor.WHITE));
        }

        soldier.customName(nameplate);
        soldier.setCustomNameVisible(true);
    }

    private Component appendSpaceIfNeeded(Component component, boolean needed) {
        return needed ? component.append(Component.text(" ")) : component;
    }

    private Component healthBar(double health, double maxHealth) {
        int segments = this.settings.nameplateHealthBarSegments();
        double ratio = Math.max(0.0D, Math.min(1.0D, health / maxHealth));
        int filledSegments = Math.max(0, Math.min(segments, (int) Math.round(ratio * segments)));
        int lostSegments = segments - filledSegments;

        return Component.text(this.settings.nameplateBarPrefix(), namedColor(this.settings.nameplateBarBracketColor(), NamedTextColor.DARK_GRAY))
                .append(Component.text(repeatSymbol(this.settings.nameplateBarFilledSymbol(), filledSegments), healthColor(ratio)))
                .append(Component.text(repeatSymbol(this.settings.nameplateBarLostSymbol(), lostSegments), namedColor(this.settings.nameplateBarLostColor(), NamedTextColor.RED)))
                .append(Component.text(this.settings.nameplateBarSuffix(), namedColor(this.settings.nameplateBarBracketColor(), NamedTextColor.DARK_GRAY)));
    }

    private NamedTextColor healthColor(double ratio) {
        if( ratio <= this.settings.nameplateLowThreshold() ) {
            return namedColor(this.settings.nameplateBarLowColor(), NamedTextColor.RED);
        }

        if( ratio < this.settings.nameplateHealthyThreshold() ) {
            return namedColor(this.settings.nameplateBarInjuredColor(), NamedTextColor.YELLOW);
        }

        return namedColor(this.settings.nameplateBarHealthyColor(), NamedTextColor.GREEN);
    }

    private NamedTextColor namedColor(String configured, NamedTextColor fallback) {
        if( configured == null || configured.isBlank() ) {
            return fallback;
        }

        NamedTextColor color = NamedTextColor.NAMES.value(configured.toLowerCase(Locale.ROOT));
        return color == null ? fallback : color;
    }

    private String repeatSymbol(String symbol, int count) {
        if( count <= 0 ) {
            return "";
        }

        String safeSymbol = symbol == null || symbol.isEmpty() ? "|" : symbol;
        return safeSymbol.repeat(count);
    }

    private String formatHealth(double value) {
        double rounded = Math.rint(value);
        if( Math.abs(value - rounded) < 0.05D ) {
            return Long.toString(Math.round(rounded));
        }

        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void killSoldier(ArmorStand soldier) {
        Optional<ClayTeam> team = getTeam(soldier);
        ClaySoldierRole role = getRole(soldier);
        Location location = soldier.getLocation();
        World world = soldier.getWorld();

        playSound(location, Sound.BLOCK_GRAVEL_BREAK, 1.0F, 0.75F);
        spawnParticle(Particle.BLOCK, location.clone().add(0.0D, 0.35D, 0.0D), scaledCount(16), 0.16D, 0.18D, 0.16D,
                Material.CLAY.createBlockData());
        team.ifPresent(value -> spawnParticle(Particle.DUST, location.clone().add(0.0D, 0.45D, 0.0D), scaledCount(10), 0.18D, 0.16D, 0.18D,
                new Particle.DustOptions(value.armorColor(), 0.8F)));

        if( this.settings.dropDollOnDeath() ) {
            team.ifPresent(value -> world.dropItemNaturally(location, this.items.createSoldierDoll(value, role, 1, getModifiers(soldier))));
        }

        removeSoldier(soldier);
    }

    private void removeSoldier(ArmorStand soldier) {
        forgetSoldier(soldier.getUniqueId());
        soldier.remove();
    }

    private void forgetSoldier(UUID id) {
        this.activeSoldiers.remove(id);
        this.brains.remove(id);
    }

    private SoldierBrain brain(ArmorStand soldier) {
        return this.brains.computeIfAbsent(soldier.getUniqueId(), ignored -> new SoldierBrain());
    }

    private ItemStack itemFor(Material material) {
        return new ItemStack(material == null ? Material.AIR : material);
    }

    private boolean enabled(AttackStyle style) {
        return this.settings.attack(style.key).enabled();
    }

    private void maybeHop(ArmorStand soldier, double chance) {
        if( !this.settings.useJumping() || this.random.nextDouble() > chance ) {
            return;
        }

        Location hopLocation = soldier.getLocation().clone().add(0.0D, this.settings.jumpHeight() * 0.55D, 0.0D);
        if( moveTo(soldier, hopLocation) ) {
            soldier.setVelocity(new Vector(0.0D, this.settings.jumpVelocityY() * 0.75D, 0.0D));
            spawnParticle(Particle.CLOUD, soldier.getLocation(), scaledCount(2), 0.05D, 0.02D, 0.05D, 0.005D);
        }
    }

    private double formationRangeSquared() {
        double range = this.settings.followRange() * this.settings.formationScanRangeMultiplier();
        return range * range;
    }

    private int scaledCount(int baseCount) {
        return Math.max(0, (int) Math.round(baseCount * this.settings.particleScale()));
    }

    private void playSound(Location location, Sound sound, float volume, float pitch) {
        if( this.settings.useSounds() ) {
            location.getWorld().playSound(location, sound, volume * this.settings.soundVolume(), pitch);
        }
    }

    private void spawnParticle(Particle particle, Location location, int count) {
        if( this.settings.useParticles() && count > 0 ) {
            location.getWorld().spawnParticle(particle, location, count);
        }
    }

    private void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if( this.settings.useParticles() && count > 0 ) {
            location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    private <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, T data) {
        if( this.settings.useParticles() && count > 0 ) {
            location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, data);
        }
    }

    private <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        if( this.settings.useParticles() && count > 0 ) {
            location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
        }
    }

    private void applyPose(ArmorStand soldier, SoldierBrain brain, ClaySoldierRole role) {
        if( !this.settings.useAnimations() ) {
            soldier.setHeadPose(new EulerAngle(0.0D, 0.0D, 0.0D));
            soldier.setBodyPose(new EulerAngle(0.0D, 0.0D, 0.0D));
            soldier.setRightArmPose(new EulerAngle(0.0D, 0.0D, 0.0D));
            soldier.setLeftArmPose(new EulerAngle(0.0D, 0.0D, 0.0D));
            soldier.setRightLegPose(new EulerAngle(0.0D, 0.0D, 0.0D));
            soldier.setLeftLegPose(new EulerAngle(0.0D, 0.0D, 0.0D));
            return;
        }

        double phase = (soldier.getTicksLived() + Math.abs(soldier.getUniqueId().getLeastSignificantBits() % 20L)) * this.settings.animationSpeed();
        double armSwing = Math.sin(phase) * this.settings.runArmSwing();
        double legSwing = Math.sin(phase + Math.PI) * this.settings.runLegSwing();

        EulerAngle head = new EulerAngle(0.0D, 0.0D, 0.0D);
        EulerAngle body = new EulerAngle(0.0D, 0.0D, 0.0D);
        EulerAngle rightArm = new EulerAngle(-0.15D, 0.0D, 0.08D);
        EulerAngle leftArm = new EulerAngle(-0.10D, 0.0D, -0.08D);
        EulerAngle rightLeg = new EulerAngle(0.05D, 0.0D, 0.0D);
        EulerAngle leftLeg = new EulerAngle(-0.05D, 0.0D, 0.0D);

        if( brain.animation == AnimationState.RUN || brain.movedThisTick ) {
            body = new EulerAngle(0.08D, 0.0D, Math.sin(phase) * this.settings.runBodySway());
            rightArm = new EulerAngle(-0.35D + armSwing, 0.0D, 0.10D);
            leftArm = new EulerAngle(-0.35D - armSwing, 0.0D, -0.10D);
            rightLeg = new EulerAngle(legSwing, 0.0D, 0.0D);
            leftLeg = new EulerAngle(-legSwing, 0.0D, 0.0D);
        }

        switch( brain.animation ) {
            case WINDUP_QUICK -> rightArm = new EulerAngle(-1.85D, 0.10D, 0.20D);
            case WINDUP_POKE -> {
                body = new EulerAngle(0.05D, 0.0D, 0.0D);
                rightArm = new EulerAngle(-1.28D, 0.0D, 0.0D);
                leftArm = new EulerAngle(-0.55D, 0.0D, -0.20D);
            }
            case WINDUP_SWEEP -> {
                body = new EulerAngle(0.0D, 0.0D, 0.28D);
                rightArm = new EulerAngle(-1.20D, -0.60D, 0.85D);
                leftArm = new EulerAngle(-0.35D, 0.0D, -0.30D);
            }
            case WINDUP_BASH -> {
                body = new EulerAngle(0.15D, 0.0D, -0.10D);
                leftArm = new EulerAngle(-1.50D, 0.0D, -0.45D);
                rightArm = new EulerAngle(-0.55D, 0.10D, 0.15D);
            }
            case WINDUP_LEAP -> {
                body = new EulerAngle(0.22D, 0.0D, 0.0D);
                rightArm = new EulerAngle(-2.25D, 0.10D, 0.0D);
                leftArm = new EulerAngle(-0.75D, 0.0D, -0.20D);
                rightLeg = new EulerAngle(-0.55D, 0.0D, 0.0D);
                leftLeg = new EulerAngle(0.35D, 0.0D, 0.0D);
            }
            case WINDUP_SLING -> {
                body = new EulerAngle(0.0D, 0.0D, -0.18D);
                rightArm = new EulerAngle(-2.05D, 0.45D, 0.35D);
                leftArm = new EulerAngle(-0.35D, 0.0D, -0.10D);
            }
            case DODGE -> {
                body = new EulerAngle(0.0D, 0.0D, brain.flankDirection * 0.35D);
                rightArm = new EulerAngle(-0.70D, 0.0D, 0.65D);
                leftArm = new EulerAngle(-0.70D, 0.0D, -0.65D);
            }
            case HIT -> {
                head = new EulerAngle(0.22D, 0.0D, 0.0D);
                body = new EulerAngle(-0.15D, 0.0D, 0.0D);
            }
            case IDLE, RUN -> {
                // Base pose already applied.
            }
        }

        if( role == ClaySoldierRole.GUARD && brain.animation == AnimationState.IDLE ) {
            leftArm = new EulerAngle(-1.25D, 0.0D, -0.28D);
        }

        soldier.setHeadPose(head);
        soldier.setBodyPose(body);
        soldier.setRightArmPose(rightArm);
        soldier.setLeftArmPose(leftArm);
        soldier.setRightLegPose(rightLeg);
        soldier.setLeftLegPose(leftLeg);
    }

    private enum AttackStyle {
        QUICK("quick", AnimationState.WINDUP_QUICK, Sound.ENTITY_PLAYER_ATTACK_WEAK),
        POKE("poke", AnimationState.WINDUP_POKE, Sound.ENTITY_PLAYER_ATTACK_WEAK),
        LEAP("leap", AnimationState.WINDUP_LEAP, Sound.ENTITY_SLIME_JUMP_SMALL),
        SWEEP("sweep", AnimationState.WINDUP_SWEEP, Sound.ENTITY_PLAYER_ATTACK_SWEEP),
        SHIELD_BASH("shield-bash", AnimationState.WINDUP_BASH, Sound.ITEM_SHIELD_BLOCK),
        SLING("sling", AnimationState.WINDUP_SLING, Sound.ENTITY_SNOWBALL_THROW);

        private final String key;
        private final AnimationState animation;
        private final Sound windupSound;

        AttackStyle(String key, AnimationState animation, Sound windupSound) {
            this.key = key;
            this.animation = animation;
            this.windupSound = windupSound;
        }
    }

    private enum AnimationState {
        IDLE,
        RUN,
        HIT,
        DODGE,
        WINDUP_QUICK,
        WINDUP_POKE,
        WINDUP_LEAP,
        WINDUP_SWEEP,
        WINDUP_BASH,
        WINDUP_SLING
    }

    private enum TacticalFormation {
        NONE,
        SHIELD_WALL,
        SPEAR_WALL,
        COMBINED_SPEAR_WALL,
        SUPPORT_HIDE,
        FLANK_SUPPORT;

        private boolean isActive() {
            return this != NONE;
        }

        private boolean isWall() {
            return this == SHIELD_WALL || this == SPEAR_WALL || this == COMBINED_SPEAR_WALL;
        }
    }

    private record PathNode(int x, int yHalf, int z) {
        private double y() {
            return this.yHalf / 2.0D;
        }
    }

    private static final class PathSearchNode {
        private final PathNode node;
        private final PathSearchNode parent;
        private final double gCost;
        private final double fCost;

        private PathSearchNode(PathNode node, PathSearchNode parent, double gCost, double fCost) {
            this.node = node;
            this.parent = parent;
            this.gCost = gCost;
            this.fCost = fCost;
        }
    }

    private static final class SoldierBrain {
        private int attackCooldownTicks;
        private int dodgeCooldownTicks;
        private int windupTicks;
        private int animationTicks;
        private int flankTicks;
        private int flankDirection = 1;
        private UUID queuedTargetId;
        private AttackStyle queuedAttack;
        private AnimationState animation = AnimationState.IDLE;
        private TacticalFormation activeFormation = TacticalFormation.NONE;
        private boolean movedThisTick;
        private List<Location> path = List.of();
        private int pathIndex;
        private Location pathTarget;
        private int pathRefreshTicks;

        private void tick(int tickPeriod) {
            this.attackCooldownTicks = Math.max(0, this.attackCooldownTicks - tickPeriod);
            this.dodgeCooldownTicks = Math.max(0, this.dodgeCooldownTicks - tickPeriod);
            this.animationTicks = Math.max(0, this.animationTicks - tickPeriod);
            this.flankTicks = Math.max(0, this.flankTicks - tickPeriod);
            this.pathRefreshTicks = Math.max(0, this.pathRefreshTicks - tickPeriod);

            if( this.animationTicks == 0 && this.windupTicks <= 0 ) {
                this.animation = AnimationState.IDLE;
            }
        }

        private void startFlank(Random random, ClaySoldierSettings settings) {
            this.flankTicks = settings.flankDurationMinTicks() + (settings.flankDurationRange() == 0 ? 0 : random.nextInt(settings.flankDurationRange()));
            this.flankDirection = random.nextBoolean() ? 1 : -1;
        }

        private void clearQueuedAttack() {
            this.queuedTargetId = null;
            this.queuedAttack = null;
            this.windupTicks = 0;
        }

        private void clearPath() {
            this.path = List.of();
            this.pathIndex = 0;
            this.pathTarget = null;
            this.pathRefreshTicks = 0;
        }
    }
}
