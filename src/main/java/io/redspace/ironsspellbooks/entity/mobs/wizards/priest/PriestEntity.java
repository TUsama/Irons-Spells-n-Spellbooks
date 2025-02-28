package io.redspace.ironsspellbooks.entity.mobs.wizards.priest;

import io.redspace.ironsspellbooks.IronsSpellbooks;
import io.redspace.ironsspellbooks.entity.mobs.SupportMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.NeutralWizard;
import io.redspace.ironsspellbooks.entity.mobs.goals.*;
import io.redspace.ironsspellbooks.registries.AttributeRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.spells.SpellType;
import io.redspace.ironsspellbooks.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

public class PriestEntity extends NeutralWizard implements VillagerDataHolder, SupportMob, HomeOwner {
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(PriestEntity.class, EntityDataSerializers.VILLAGER_DATA);
    private static final EntityDataAccessor<Boolean> DATA_VILLAGER_UNHAPPY = SynchedEntityData.defineId(PriestEntity.class, EntityDataSerializers.BOOLEAN);
    public GoalSelector supportTargetSelector;
    private int unhappyTimer;

    public PriestEntity(EntityType<? extends AbstractSpellCastingMob> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        xpReward = 15;

    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(1, new PriestDefenseGoal(this));
        this.goalSelector.addGoal(2, new WizardSupportGoal<>(this, 1.5f, 100, 180)
                .setSpells(
                        List.of(SpellType.BLESSING_OF_LIFE_SPELL, SpellType.BLESSING_OF_LIFE_SPELL, SpellType.HEALING_CIRCLE_SPELL),
                        List.of(SpellType.FORTIFY_SPELL)
                ));
        this.goalSelector.addGoal(3, new WizardAttackGoal(this, 1.5f, 35, 70)
                .setSpells(
                        List.of(SpellType.WISP_SPELL, SpellType.GUIDING_BOLT_SPELL, SpellType.GUIDING_BOLT_SPELL),
                        List.of(SpellType.GUST_SPELL),
                        List.of(),
                        List.of(SpellType.HEAL_SPELL))
                .setSpellQuality(0.3f, 0.5f)
                .setDrinksPotions());
        this.goalSelector.addGoal(5, new RoamVillageGoal(this, 30, 1f));
        this.goalSelector.addGoal(6, new ReturnToHomeAtNightGoal<>(this, 1f));
        this.goalSelector.addGoal(7, new PatrolNearLocationGoal(this, 30, 1f));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new WizardRecoverGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new GenericDefendVillageTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false, (mob) -> mob instanceof Enemy && !(mob instanceof Creeper)));
        this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, false));

        this.supportTargetSelector = new GoalSelector(this.level.getProfilerSupplier());
        this.supportTargetSelector.addGoal(0, new FindSupportableTargetGoal<>(this, LivingEntity.class, true,
                (mob) -> !isAngryAt(mob) && mob.getHealth() * 1.25f < mob.getMaxHealth() && (mob.getType().is(ModTags.VILLAGE_ALLIES) || mob instanceof Player))
        );
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        RandomSource randomsource = pLevel.getRandom();
        this.populateDefaultEquipmentSlots(randomsource, pDifficulty);
        this.setHome(this.blockPosition());
        IronsSpellbooks.LOGGER.debug("Priest new home: {}", this.getHome());
        return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData, pDataTag);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource pRandom, DifficultyInstance pDifficulty) {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ItemRegistry.PRIEST_HELMET.get()));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ItemRegistry.PRIEST_CHESTPLATE.get()));
        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        this.setDropChance(EquipmentSlot.CHEST, 0.0F);
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(AttributeRegistry.CAST_TIME_REDUCTION.get(), 1.5)
                .add(Attributes.MOVEMENT_SPEED, .23);
    }

    @Override
    protected PathNavigation createNavigation(Level pLevel) {
        return new GroundPathNavigation(this, pLevel) {
            @Override
            protected PathFinder createPathFinder(int pMaxVisitedNodes) {
                this.nodeEvaluator = new WalkNodeEvaluator();
                this.nodeEvaluator.setCanPassDoors(true);
                this.nodeEvaluator.setCanOpenDoors(true);
                return new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
            }
//
//            @Override
//            public NodeEvaluator getNodeEvaluator() {
//                var nodeEvalutator = super.getNodeEvaluator();
//                nodeEvalutator.setCanPassDoors(true);
//                nodeEvalutator.setCanOpenDoors(true);
//                return nodeEvalutator;
//            }
        };
    }

    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VILLAGER_DATA, new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
        this.entityData.define(DATA_VILLAGER_UNHAPPY, false);
    }

    public void setVillagerData(VillagerData villagerdata) {
        //VillagerData villagerdata = this.getVillagerData();
        villagerdata.setProfession(VillagerProfession.NONE);
        this.entityData.set(DATA_VILLAGER_DATA, villagerdata);
    }

    public boolean isUnhappy() {
        return this.entityData.get(DATA_VILLAGER_UNHAPPY);
    }

    public @NotNull VillagerData getVillagerData() {
        return this.entityData.get(DATA_VILLAGER_DATA);
    }

    LivingEntity supportTarget;

    @org.jetbrains.annotations.Nullable
    @Override
    public LivingEntity getSupportTarget() {
        return supportTarget;
    }

    @Override
    public void setSupportTarget(LivingEntity target) {
        this.supportTarget = target;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        //Vanilla does this seemingly-excessive tick count solution. maybe there's a method to the madness
        if (this.tickCount % 4 == 0 && this.tickCount > 1) {
            this.supportTargetSelector.tick();
        }
        if (this.tickCount % 60 == 0) {
            this.level.getEntities(this, this.getBoundingBox().inflate(this.getAttributeValue(Attributes.FOLLOW_RANGE)), (entity) -> entity instanceof Enemy && !(entity instanceof Creeper)).forEach((enemy) -> {
                if (enemy instanceof Mob mob)
                    if (mob.getTarget() == null)
                        if (TargetingConditions.forCombat().test(mob, this))
                            mob.setTarget(this);
            });
        }
        if (unhappyTimer > 0)
            if (--unhappyTimer == 0)
                this.entityData.set(DATA_VILLAGER_UNHAPPY, false);
//        this.level.getProfiler().push("priestBrain");
//        this.getBrain().tick((ServerLevel) this.level, this);
//        this.level.getProfiler().pop();
//        this.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.CORE));

    }

    @Override
    protected InteractionResult mobInteract(Player pPlayer, InteractionHand pHand) {
        setUnhappy();
        return super.mobInteract(pPlayer, pHand);
    }

    public void setUnhappy() {
        if (!level.isClientSide) {
            this.playSound(SoundEvents.VILLAGER_NO, this.getSoundVolume(), this.getVoicePitch());
            unhappyTimer = 20;
            this.entityData.set(DATA_VILLAGER_UNHAPPY, true);
        }
    }

    BlockPos homePos;

    @org.jetbrains.annotations.Nullable
    @Override
    public BlockPos getHome() {
        return homePos;
    }

    @Override
    public void setHome(BlockPos homePos) {
        this.homePos = homePos;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        serializeHome(this, pCompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        deserializeHome(this, pCompound);
    }

    class PriestDefenseGoal extends Goal {
        protected final PriestEntity mob;
        protected int attackCooldown = 0;

        public PriestDefenseGoal(PriestEntity abstractSpellCastingMob) {
            this.mob = abstractSpellCastingMob;
        }

        public boolean canUse() {
            LivingEntity livingentity = this.mob.getTarget();
            //IronsSpellbooks.LOGGER.debug("{} PriestDefenseGoal.canUse:", attackCooldown);
            if (livingentity != null && --attackCooldown <= 0 && livingentity.isAlive() && shouldAreaAttack(livingentity)) {
                //IronsSpellbooks.LOGGER.debug("true ({})", livingentity.getName().getString());
                return false;
            } else {
                return false;
            }
        }

        public boolean shouldAreaAttack(LivingEntity livingEntity) {
            if (mob.isCasting()) {
                //IronsSpellbooks.LOGGER.debug("shouldAreaAttack: already casting");
                return false;
            }
            var d = livingEntity.distanceToSqr(mob);
            var inRange = d < 5 * 5;
            if (!inRange)
                return false;
            //IronsSpellbooks.LOGGER.debug("shouldAreaAttack: in range");

            if (livingEntity.getType() == EntityType.VINDICATOR) {
                //IronsSpellbooks.LOGGER.debug("VINDICATOR!");
                start();
                return false;
            }

            //anti-rush
            if (this.mob.getHealth() / this.mob.getMaxHealth() < .25f && mob.level.getEntities(mob, mob.getBoundingBox().inflate(3f), (entity -> entity instanceof Enemy)).size() > 1) {
                start();
                return false;
            }

            //swarm control
            int mobCount = livingEntity.level.getEntities(livingEntity, livingEntity.getBoundingBox().inflate(6f), (entity -> entity instanceof Enemy)).size();
            if (mobCount >= 2)
                start();
            return false;
        }

        @Override
        public void start() {
            this.attackCooldown = 40 + mob.random.nextInt(30);
            int spellLevel = (int) (SpellType.GUST_SPELL.getMaxLevel() * .5f);
            var spellType = SpellType.GUST_SPELL;

            mob.initiateCastSpell(spellType, spellLevel);
        }
    }

    /*
    Brain Testing
     */
/*
    public Brain<PriestEntity> getBrain() {
        return (Brain<PriestEntity>) super.getBrain();
    }

    protected Brain.Provider<PriestEntity> brainProvider() {
        return Brain.provider(
                //Memories
                ImmutableList.of(
                        MemoryModuleType.WALK_TARGET,
                        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                        MemoryModuleType.PATH,
                        //MemoryModuleType.MEETING_POINT,
                        MemoryModuleType.LOOK_TARGET,
                        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES
                ),
                //Sensors
                ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY));
    }
    //ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.NEAREST_BED, SensorType.HURT_BY, SensorType.VILLAGER_HOSTILES, SensorType.VILLAGER_BABIES, SensorType.SECONDARY_POIS, SensorType.GOLEM_DETECTED);

    protected Brain<?> makeBrain(Dynamic<?> pDynamic) {
        Brain<PriestEntity> brain = this.brainProvider().makeBrain(pDynamic);
        this.registerBrainGoals(brain);
        return brain;
    }
//
//    public void refreshBrain(ServerLevel pServerLevel) {
//        Brain<PriestEntity> brain = this.getBrain();
//        brain.stopAll(pServerLevel, this);
//        this.brain = brain.copyWithoutBehaviors();
//        this.registerBrainGoals(this.getBrain());
//    }

    private void registerBrainGoals(Brain<PriestEntity> brain) {

        ImmutableList<Pair<Integer, ? extends Behavior<? super PriestEntity>>> core = ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
                Pair.of(0, new InteractWithDoor()),

                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(1, new MoveToTargetSink())

//                Pair.of(0, new RunSometimes<>(new SetEntityLookTarget(EntityType.PLAYER, 6.0F), UniformInt.of(30, 60))),
//
//                //Pair.of(0, new SetRaidStatus()),
//                Pair.of(3, new RunOne<>(ImmutableList.of(
//                        Pair.of(new RandomStroll(1.0F), 2),
//                        Pair.of(new SetWalkTargetFromLookTarget(1.0F, 3), 2),
//                        Pair.of(new DoNothing(30, 60), 1)
//                )))
//                Pair.of(10, new AcquirePoi((p_217499_) -> {
//                    return p_217499_.is(PoiTypes.HOME);
//                }, MemoryModuleType.HOME, false, Optional.of((byte) 14))),
//                Pair.of(10, new AcquirePoi((p_217497_) -> {
//                    return p_217497_.is(PoiTypes.MEETING);
//                }, MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14)))
        );
        ImmutableList<Pair<Integer, ? extends Behavior<? super PriestEntity>>> idle = ImmutableList.of(
                Pair.of(0, new RunSometimes<>(new SetEntityLookTarget(EntityType.PLAYER, 6.0F), UniformInt.of(30, 60))),

                Pair.of(3, new RunOne<>(ImmutableList.of(
                        Pair.of(new RandomStroll(1.0F), 2),
                        Pair.of(new SetWalkTargetFromLookTarget(1.0F, 3), 2),
                        Pair.of(new DoNothing(30, 60), 1)
                )))

        );
        brain.addActivity(Activity.CORE, core);
        brain.addActivity(Activity.IDLE, idle);
//        brain.addActivityWithConditions(Activity.MEET, VillagerGoalPackages.getMeetPackage(villagerprofession, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
//        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(villagerprofession, 0.5F));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
//
//        pBrain.addActivity(Activity.CORE, 0,
//                ImmutableList.of(
//                        new Swim(0.8F),
//                        new AnimalPanic(2.0F),
//                        new LookAtTargetSink(45, 90),
//                        new MoveToTargetSink(),
//                        new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS),
//                        new CountDownCooldownTicks(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS),
//                        new CountDownCooldownTicks(MemoryModuleType.RAM_COOLDOWN_TICKS)
//                ));
//
//        pBrain.addActivityWithConditions(Activity.IDLE,
//                ImmutableList.of(
//                        Pair.of(0, new RunSometimes<>(new SetEntityLookTarget(EntityType.PLAYER, 6.0F), UniformInt.of(30, 60))),
//                        Pair.of(0, new AnimalMakeLove(EntityType.GOAT, 1.0F)),
//                        Pair.of(1, new FollowTemptation((p_149446_) -> {
//                            return 1.25F;
//                        })),
//                        Pair.of(2, new BabyFollowAdult<>(ADULT_FOLLOW_RANGE, 1.25F)),
//                        Pair.of(3, new RunOne<>(ImmutableList.of(
//                                Pair.of(new RandomStroll(1.0F), 2),
//                                Pair.of(new SetWalkTargetFromLookTarget(1.0F, 3), 2),
//                                Pair.of(new DoNothing(30, 60), 1)
//                        )))
//                ),
//                ImmutableSet.of(
//                        Pair.of(MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_ABSENT),
//                        Pair.of(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT)));
    }
 */
}
