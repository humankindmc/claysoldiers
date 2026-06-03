package com.humankindgames.claysoldiers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

            if( brain.windupTicks > 0 ) {
                tickWindup(soldier, brain, role);
            } else {
                Optional<ArmorStand> nearestEnemy = findNearestEnemy(soldier, role);
                if( nearestEnemy.isPresent() ) {
                    engage(soldier, nearestEnemy.get(), brain, role);
                } else {
                    wander(soldier, brain, role);
                }
            }

            applyPose(soldier, brain, role);
            brain.movedThisTick = false;
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
        TacticalFormation tacticalFormation = tacticalFormation(soldier, role);
        face(soldier, target.getLocation());

        if( distanceSquared <= attackRange * attackRange && brain.attackCooldownTicks <= 0 ) {
            queueAttack(soldier, target, brain, role, Math.sqrt(distanceSquared));
            return;
        }

        if( this.settings.useFlanking() && brain.flankTicks <= 0 && this.random.nextDouble() < roleTuning.flankChance() * this.settings.flankChanceScale() ) {
            brain.startFlank(this.random, this.settings);
        }

        Location goal = brain.flankTicks > 0
                ? flankPosition(soldier, target, brain)
                : formationPosition(soldier, target, role, tacticalFormation);
        double step = this.settings.moveStep() * speedMultiplier(soldier, roleTuning);
        if( tacticalFormation.isActive() ) {
            step *= this.settings.tacticalFormationSpeedMultiplier();
        }

        if( distanceSquared < (attackRange + 0.8D) * (attackRange + 0.8D) && role != ClaySoldierRole.GUARD ) {
            step *= this.settings.closeCombatStepMultiplier();
        }

        if( moveToward(soldier, goal, step) ) {
            brain.movedThisTick = true;
            maybeHop(soldier, this.settings.chaseJumpChance());
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

        TacticalFormation tacticalFormation = tacticalFormation(soldier, role);
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
        if( moveToward(soldier, target, this.settings.moveStep() * speedMultiplier(soldier, this.settings.role(role)) * this.settings.wanderStepMultiplier()) ) {
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

        double distance = switch( tacticalFormation ) {
            case SHIELD_WALL -> this.settings.shieldWallDistance() + row * this.settings.formationRowSpacing();
            case SPEAR_WALL -> this.settings.spearWallDistance() + row * this.settings.formationRowSpacing();
            case COMBINED_SPEAR_WALL, SUPPORT_HIDE -> this.settings.supportBehindWallDistance() + row * this.settings.formationRowSpacing();
            case FLANK_SUPPORT -> this.settings.shieldWallDistance() + row * this.settings.formationRowSpacing();
            case NONE -> this.settings.attackRange();
        };

        Location base = target.getLocation().clone().add(away.multiply(distance));
        if( tacticalFormation == TacticalFormation.FLANK_SUPPORT ) {
            int flankSide = slot % 2 == 0 ? 1 : -1;
            return base.add(side.multiply(flankSide * (this.settings.flankProtectionWidth() + row * this.settings.formationLateralSpacing())));
        }

        return base.add(side.multiply(column * this.settings.formationLateralSpacing()));
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

    private boolean moveToward(ArmorStand soldier, Location target, double stepSize) {
        Location current = soldier.getLocation();
        Vector direction = target.toVector().subtract(current.toVector());
        direction.setY(0.0D);

        if( direction.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED ) {
            return false;
        }

        direction.normalize();
        Location next = current.clone().add(direction.clone().multiply(stepSize));
        next.setDirection(direction);
        if( moveTo(soldier, next) ) {
            return true;
        }

        return tryBlockedMovement(soldier, current, direction, stepSize);
    }

    private boolean moveTo(ArmorStand soldier, Location location) {
        if( canOccupy(location) ) {
            soldier.teleport(location);
            return true;
        }

        return false;
    }

    private boolean tryBlockedMovement(ArmorStand soldier, Location current, Vector direction, double stepSize) {
        Vector firstSide = perpendicular(direction).multiply(this.random.nextBoolean() ? 1.0D : -1.0D);
        Vector secondSide = firstSide.clone().multiply(-1.0D);
        Vector reverse = direction.clone().multiply(-1.0D);

        if( tryStep(soldier, current, firstSide, stepSize * 0.75D) ) {
            return true;
        }

        if( tryStep(soldier, current, secondSide, stepSize * 0.75D) ) {
            return true;
        }

        if( tryStep(soldier, current, reverse, stepSize * 0.60D) ) {
            return true;
        }

        faceDirection(soldier, reverse);
        return false;
    }

    private boolean tryStep(ArmorStand soldier, Location current, Vector direction, double stepSize) {
        Location next = current.clone().add(direction.clone().multiply(stepSize));
        next.setDirection(direction);
        return moveTo(soldier, next);
    }

    private boolean canOccupy(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        return feet.isPassable() && head.isPassable();
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
        private boolean movedThisTick;

        private void tick(int tickPeriod) {
            this.attackCooldownTicks = Math.max(0, this.attackCooldownTicks - tickPeriod);
            this.dodgeCooldownTicks = Math.max(0, this.dodgeCooldownTicks - tickPeriod);
            this.animationTicks = Math.max(0, this.animationTicks - tickPeriod);
            this.flankTicks = Math.max(0, this.flankTicks - tickPeriod);

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
    }
}
