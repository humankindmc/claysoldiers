package com.humankindgames.claysoldiers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
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

    public void registerLoadedSoldiers() {
        for( World world : this.plugin.getServer().getWorlds() ) {
            for( ArmorStand armorStand : world.getEntitiesByClass(ArmorStand.class) ) {
                registerIfSoldier(armorStand);
            }
        }
    }

    public void registerIfSoldier(Entity entity) {
        if( isSoldier(entity) ) {
            this.activeSoldiers.add(entity.getUniqueId());
            this.brains.computeIfAbsent(entity.getUniqueId(), ignored -> new SoldierBrain());
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

        return ClaySoldierRole.fromKey(roleKeyValue).orElse(ClaySoldierRole.WARRIOR);
    }

    public List<ArmorStand> spawnSoldiers(ClayTeam team, Location baseLocation, int count) {
        return spawnSoldiers(team, ClaySoldierRole.WARRIOR, baseLocation, count);
    }

    public List<ArmorStand> spawnSoldiers(ClayTeam team, ClaySoldierRole role, Location baseLocation, int count) {
        int boundedCount = Math.max(1, Math.min(this.settings.maxSpawnPerUse(), count));
        return java.util.stream.IntStream.range(0, boundedCount)
                .mapToObj(i -> spawnSoldier(team, role, randomizedSpawn(baseLocation)))
                .toList();
    }

    public ArmorStand spawnSoldier(ClayTeam team, Location location) {
        return spawnSoldier(team, ClaySoldierRole.WARRIOR, location);
    }

    public ArmorStand spawnSoldier(ClayTeam team, ClaySoldierRole role, Location location) {
        World world = location.getWorld();
        if( world == null ) {
            throw new IllegalArgumentException("Cannot spawn a clay soldier without a world");
        }

        ArmorStand stand = world.spawn(location, ArmorStand.class);
        configureSoldier(stand, team, role);
        this.activeSoldiers.add(stand.getUniqueId());
        this.brains.put(stand.getUniqueId(), new SoldierBrain());
        world.playSound(stand.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.0F, 1.0F + this.random.nextFloat() * 0.2F);
        world.spawnParticle(Particle.DUST, stand.getLocation().clone().add(0.0D, 0.4D, 0.0D), 8, 0.12D, 0.12D, 0.12D,
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
        if( attacker instanceof ArmorStand attackingSoldier && isSoldier(attackingSoldier) && tryDodge(soldier, attackingSoldier, role) ) {
            return;
        }

        double health = getHealth(soldier) - Math.max(0.0D, damage);
        if( health <= 0.0D ) {
            killSoldier(soldier);
            return;
        }

        soldier.getPersistentDataContainer().set(this.healthKey, PersistentDataType.DOUBLE, health);
        soldier.getWorld().playSound(soldier.getLocation(), Sound.ENTITY_ARMOR_STAND_HIT, 0.55F, 1.25F);
        SoldierBrain brain = brain(soldier);
        brain.animation = AnimationState.HIT;
        brain.animationTicks = 8;

        getTeam(soldier).ifPresent(team -> soldier.getWorld().spawnParticle(Particle.DUST,
                soldier.getLocation().clone().add(0.0D, 0.6D, 0.0D),
                3,
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

    private void configureSoldier(ArmorStand stand, ClayTeam team, ClaySoldierRole role) {
        stand.setSmall(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(true);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        stand.setCollidable(false);
        stand.setCustomNameVisible(false);
        stand.customName(Component.text(team.displayName() + " " + role.displayName(), team.textColor()));

        PersistentDataContainer data = stand.getPersistentDataContainer();
        data.set(this.entityTypeKey, PersistentDataType.STRING, SOLDIER_ENTITY_TYPE);
        data.set(this.teamKey, PersistentDataType.STRING, team.key());
        data.set(this.roleKey, PersistentDataType.STRING, role.key());
        data.set(this.healthKey, PersistentDataType.DOUBLE, this.settings.maxHealth() * role.healthMultiplier());

        EntityEquipment equipment = stand.getEquipment();
        if( equipment != null ) {
            equipment.setHelmet(this.items.createArmorPiece(Material.LEATHER_HELMET, team));
            equipment.setChestplate(this.items.createArmorPiece(Material.LEATHER_CHESTPLATE, team));
            equipment.setLeggings(this.items.createArmorPiece(Material.LEATHER_LEGGINGS, team));
            equipment.setBoots(this.items.createArmorPiece(Material.LEATHER_BOOTS, team));
            equipment.setItemInMainHand(new ItemStack(role.mainHand()));
            equipment.setItemInOffHand(role.offHand() == null ? new ItemStack(Material.AIR) : new ItemStack(role.offHand()));
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
        double searchRange = Math.max(this.settings.followRange(), attackRange(role) + 3.0D);
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
        double attackRange = attackRange(role);
        face(soldier, target.getLocation());

        if( distanceSquared <= attackRange * attackRange && brain.attackCooldownTicks <= 0 ) {
            queueAttack(soldier, target, brain, role, Math.sqrt(distanceSquared));
            return;
        }

        if( brain.flankTicks <= 0 && this.random.nextDouble() < role.flankChance() * 0.16D ) {
            brain.startFlank(this.random);
        }

        Location goal = brain.flankTicks > 0
                ? flankPosition(soldier, target, brain)
                : formationPosition(soldier, target, role);
        double step = this.settings.moveStep() * role.speedMultiplier();

        if( distanceSquared < (attackRange + 0.8D) * (attackRange + 0.8D) && role != ClaySoldierRole.GUARD ) {
            step *= 0.72D;
        }

        if( moveToward(soldier, goal, step) ) {
            brain.movedThisTick = true;
            if( brain.animationTicks <= 0 ) {
                brain.animation = AnimationState.RUN;
                brain.animationTicks = this.settings.tickPeriodTicks() + 3;
            }
        }
    }

    private void queueAttack(ArmorStand soldier, ArmorStand target, SoldierBrain brain, ClaySoldierRole role, double distance) {
        AttackStyle style = chooseAttackStyle(soldier, target, role, distance);
        brain.queuedAttack = style;
        brain.queuedTargetId = target.getUniqueId();
        brain.windupTicks = style.windupTicks;
        brain.attackCooldownTicks = this.settings.attackCooldownTicks() + style.extraCooldownTicks;
        brain.animation = style.animation;
        brain.animationTicks = style.windupTicks + 8;

        if( style == AttackStyle.LEAP ) {
            leapToward(soldier, target);
        }

        soldier.getWorld().playSound(soldier.getLocation(), style.windupSound, 0.45F, 1.0F + this.random.nextFloat() * 0.25F);
    }

    private AttackStyle chooseAttackStyle(ArmorStand soldier, ArmorStand target, ClaySoldierRole role, double distance) {
        if( role == ClaySoldierRole.SLINGER ) {
            return AttackStyle.SLING;
        }
        if( role == ClaySoldierRole.SPEARMAN ) {
            return AttackStyle.POKE;
        }
        if( role == ClaySoldierRole.GUARD && this.random.nextDouble() < 0.48D ) {
            return AttackStyle.SHIELD_BASH;
        }
        if( nearbyEnemies(target, getTeam(soldier).orElse(ClayTeam.CLAY), 1.15D) >= 2 && this.random.nextDouble() < 0.35D ) {
            return AttackStyle.SWEEP;
        }
        if( distance > 0.45D && this.random.nextDouble() < (role == ClaySoldierRole.SKIRMISHER ? 0.42D : 0.24D) ) {
            return AttackStyle.LEAP;
        }

        return AttackStyle.QUICK;
    }

    private void performAttack(ArmorStand soldier, ArmorStand target, SoldierBrain brain, ClaySoldierRole role) {
        AttackStyle style = brain.queuedAttack == null ? AttackStyle.QUICK : brain.queuedAttack;
        double maxRange = attackRange(role) + style.rangeBonus;
        if( soldier.getLocation().distanceSquared(target.getLocation()) > maxRange * maxRange ) {
            return;
        }

        double damage = this.settings.attackDamage() * role.damageMultiplier() * style.damageMultiplier;
        World world = soldier.getWorld();
        soldier.swingMainHand();

        switch( style ) {
            case SLING -> {
                drawSlingTrail(soldier, target);
                damageSoldier(target, damage, soldier);
                world.playSound(soldier.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.7F, 1.35F);
            }
            case SWEEP -> {
                damageSoldier(target, damage, soldier);
                for( ArmorStand enemy : nearbyEnemySoldiers(target, getTeam(soldier).orElse(ClayTeam.CLAY), 1.25D) ) {
                    if( !enemy.equals(target) ) {
                        damageSoldier(enemy, damage * 0.65D, soldier);
                    }
                }
                world.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().clone().add(0.0D, 0.55D, 0.0D), 1);
                world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7F, 1.6F);
            }
            case SHIELD_BASH -> {
                damageSoldier(target, damage, soldier);
                pushAway(target, soldier.getLocation(), 0.42D);
                world.playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.75F, 1.1F);
            }
            case LEAP -> {
                damageSoldier(target, damage, soldier);
                world.spawnParticle(Particle.CLOUD, soldier.getLocation(), 4, 0.08D, 0.04D, 0.08D, 0.01D);
                world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.55F, 1.4F);
            }
            case POKE, QUICK -> {
                damageSoldier(target, damage, soldier);
                world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.55F, style == AttackStyle.POKE ? 1.75F : 1.45F);
            }
        }
    }

    private void wander(ArmorStand soldier, SoldierBrain brain, ClaySoldierRole role) {
        if( this.random.nextInt(role == ClaySoldierRole.SKIRMISHER ? 3 : 5) != 0 ) {
            return;
        }

        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        Location target = soldier.getLocation().clone().add(Math.cos(angle), 0.0D, Math.sin(angle));
        if( moveToward(soldier, target, this.settings.moveStep() * role.speedMultiplier() * 0.55D) ) {
            brain.movedThisTick = true;
            brain.animation = AnimationState.RUN;
            brain.animationTicks = this.settings.tickPeriodTicks() + 3;
        }
    }

    private boolean tryDodge(ArmorStand soldier, ArmorStand attacker, ClaySoldierRole role) {
        SoldierBrain brain = brain(soldier);
        if( brain.dodgeCooldownTicks > 0 || this.random.nextDouble() > role.dodgeChance() ) {
            return false;
        }

        Vector away = horizontalDirection(attacker.getLocation(), soldier.getLocation());
        Vector side = perpendicular(away).multiply(this.random.nextBoolean() ? 1.0D : -1.0D);
        Location dodgeLocation = soldier.getLocation().clone()
                .add(side.multiply(0.52D + this.random.nextDouble() * 0.28D))
                .add(away.multiply(0.22D));

        if( !moveTo(soldier, dodgeLocation) ) {
            return false;
        }

        brain.dodgeCooldownTicks = 22 + this.random.nextInt(16);
        brain.animation = AnimationState.DODGE;
        brain.animationTicks = 12;
        soldier.getWorld().playSound(soldier.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.35F, 1.7F);
        soldier.getWorld().spawnParticle(Particle.CLOUD, soldier.getLocation().clone().add(0.0D, 0.2D, 0.0D), 3, 0.08D, 0.03D, 0.08D, 0.01D);
        return true;
    }

    private Location formationPosition(ArmorStand soldier, ArmorStand target, ClaySoldierRole role) {
        Vector away = horizontalDirection(target.getLocation(), soldier.getLocation());
        Vector side = perpendicular(away);
        int slot = formationSlot(soldier, target);
        int column = slot % 5 - 2;
        int row = slot / 5;
        double distance = Math.max(0.65D, attackRange(role) * 0.82D + role.formationDepth() + row * 0.34D);

        if( role == ClaySoldierRole.SKIRMISHER ) {
            int flankSide = brain(soldier).flankDirection == 0 ? 1 : brain(soldier).flankDirection;
            return target.getLocation().clone()
                    .add(side.clone().multiply(flankSide * (1.25D + Math.abs(column) * 0.25D)))
                    .add(away.clone().multiply(0.45D + row * 0.20D));
        }

        return target.getLocation().clone()
                .add(away.multiply(distance))
                .add(side.multiply(column * 0.50D));
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
                .filter(candidate -> candidate.getLocation().distanceSquared(target.getLocation()) <= this.settings.followRange() * this.settings.followRange())
                .sorted(Comparator.<ArmorStand>comparingInt(candidate -> getRole(candidate).ordinal()).thenComparing(Entity::getUniqueId))
                .toList();

        int index = allies.indexOf(soldier);
        return Math.max(0, index);
    }

    private Location flankPosition(ArmorStand soldier, ArmorStand target, SoldierBrain brain) {
        Vector away = horizontalDirection(target.getLocation(), soldier.getLocation());
        Vector side = perpendicular(away).multiply(brain.flankDirection);
        return target.getLocation().clone()
                .add(side.multiply(1.35D + this.random.nextDouble() * 0.35D))
                .add(away.multiply(0.38D));
    }

    private void leapToward(ArmorStand soldier, ArmorStand target) {
        Vector direction = horizontalDirection(soldier.getLocation(), target.getLocation());
        Location leapLocation = soldier.getLocation().clone().add(direction.multiply(0.42D)).add(0.0D, 0.22D, 0.0D);
        if( moveTo(soldier, leapLocation) ) {
            soldier.setVelocity(new Vector(0.0D, 0.08D, 0.0D));
            soldier.getWorld().spawnParticle(Particle.CLOUD, soldier.getLocation(), 3, 0.07D, 0.02D, 0.07D, 0.01D);
            soldier.getWorld().playSound(soldier.getLocation(), Sound.ENTITY_SLIME_JUMP_SMALL, 0.45F, 1.45F);
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
            soldier.getWorld().spawnParticle(Particle.ITEM, start.clone().add(step.clone().multiply(i)), 1, 0.01D, 0.01D, 0.01D, 0.0D,
                    new ItemStack(Material.CLAY_BALL));
        }
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
        return moveTo(soldier, next);
    }

    private boolean moveTo(ArmorStand soldier, Location location) {
        if( canOccupy(location) ) {
            soldier.teleport(location);
            return true;
        }

        return false;
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

    private double attackRange(ClaySoldierRole role) {
        return this.settings.attackRange() * role.rangeMultiplier();
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
        return health == null ? this.settings.maxHealth() * getRole(soldier).healthMultiplier() : health;
    }

    private void killSoldier(ArmorStand soldier) {
        Optional<ClayTeam> team = getTeam(soldier);
        ClaySoldierRole role = getRole(soldier);
        Location location = soldier.getLocation();
        World world = soldier.getWorld();

        world.playSound(location, Sound.BLOCK_GRAVEL_BREAK, 1.0F, 0.75F);
        world.spawnParticle(Particle.BLOCK, location.clone().add(0.0D, 0.35D, 0.0D), 16, 0.16D, 0.18D, 0.16D,
                Material.CLAY.createBlockData());
        team.ifPresent(value -> world.spawnParticle(Particle.DUST, location.clone().add(0.0D, 0.45D, 0.0D), 10, 0.18D, 0.16D, 0.18D,
                new Particle.DustOptions(value.armorColor(), 0.8F)));

        if( this.settings.dropDollOnDeath() ) {
            team.ifPresent(value -> world.dropItemNaturally(location, this.items.createSoldierDoll(value, role, 1)));
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

    private void applyPose(ArmorStand soldier, SoldierBrain brain, ClaySoldierRole role) {
        double phase = (soldier.getTicksLived() + Math.abs(soldier.getUniqueId().getLeastSignificantBits() % 20L)) * 0.34D;
        double armSwing = Math.sin(phase) * 0.65D;
        double legSwing = Math.sin(phase + Math.PI) * 0.45D;

        EulerAngle head = new EulerAngle(0.0D, 0.0D, 0.0D);
        EulerAngle body = new EulerAngle(0.0D, 0.0D, 0.0D);
        EulerAngle rightArm = new EulerAngle(-0.15D, 0.0D, 0.08D);
        EulerAngle leftArm = new EulerAngle(-0.10D, 0.0D, -0.08D);
        EulerAngle rightLeg = new EulerAngle(0.05D, 0.0D, 0.0D);
        EulerAngle leftLeg = new EulerAngle(-0.05D, 0.0D, 0.0D);

        if( brain.animation == AnimationState.RUN || brain.movedThisTick ) {
            body = new EulerAngle(0.08D, 0.0D, Math.sin(phase) * 0.06D);
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
        QUICK(5, 0, 1.00D, 0.00D, AnimationState.WINDUP_QUICK, Sound.ENTITY_PLAYER_ATTACK_WEAK),
        POKE(7, 5, 0.92D, 0.25D, AnimationState.WINDUP_POKE, Sound.ENTITY_PLAYER_ATTACK_WEAK),
        LEAP(5, 9, 1.25D, 0.15D, AnimationState.WINDUP_LEAP, Sound.ENTITY_SLIME_JUMP_SMALL),
        SWEEP(8, 12, 0.88D, 0.10D, AnimationState.WINDUP_SWEEP, Sound.ENTITY_PLAYER_ATTACK_SWEEP),
        SHIELD_BASH(7, 16, 0.78D, 0.05D, AnimationState.WINDUP_BASH, Sound.ITEM_SHIELD_BLOCK),
        SLING(10, 14, 1.00D, 0.00D, AnimationState.WINDUP_SLING, Sound.ENTITY_SNOWBALL_THROW);

        private final int windupTicks;
        private final int extraCooldownTicks;
        private final double damageMultiplier;
        private final double rangeBonus;
        private final AnimationState animation;
        private final Sound windupSound;

        AttackStyle(int windupTicks, int extraCooldownTicks, double damageMultiplier, double rangeBonus,
                    AnimationState animation, Sound windupSound) {
            this.windupTicks = windupTicks;
            this.extraCooldownTicks = extraCooldownTicks;
            this.damageMultiplier = damageMultiplier;
            this.rangeBonus = rangeBonus;
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

        private void startFlank(Random random) {
            this.flankTicks = 24 + random.nextInt(36);
            this.flankDirection = random.nextBoolean() ? 1 : -1;
        }

        private void clearQueuedAttack() {
            this.queuedTargetId = null;
            this.queuedAttack = null;
            this.windupTicks = 0;
        }
    }
}
