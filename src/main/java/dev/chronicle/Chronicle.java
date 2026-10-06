package dev.chronicle;

import dev.chronicle.entity.MatterBody;
import dev.chronicle.entity.ResonantCrystal;
import dev.chronicle.network.Wire;
import dev.chronicle.world.CrystalSeed;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Chronicle.ID)
public final class Chronicle {
    public static final String ID = "psychokinesis";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final RegistryObject<EntityType<MatterBody>> MATTER = ENTITIES.register("matter_body", () -> EntityType.Builder.<MatterBody>of(MatterBody::new, MobCategory.MISC).sized(1, 1).clientTrackingRange(12).updateInterval(1).build(ID + ":matter_body"));
    public static final RegistryObject<EntityType<ResonantCrystal>> CRYSTAL = ENTITIES.register("resonant_crystal", () -> EntityType.Builder.<ResonantCrystal>of(ResonantCrystal::new, MobCategory.MISC).sized(2, 4).clientTrackingRange(10).fireImmune().build(ID + ":resonant_crystal"));
    public static final RegistryObject<Block> CRYSTAL_MODEL = BLOCKS.register("resonant_crystal", () -> new Block(BlockBehaviour.Properties.of().strength(-1, 3600000).lightLevel(s -> 9).noOcclusion().sound(SoundType.AMETHYST)));
    public static final RegistryObject<Item> CRYSTAL_EGG = ITEMS.register("crystal_spawn_egg", () -> new CrystalSeed(new Item.Properties()));
    public Chronicle() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus); BLOCKS.register(bus); ITEMS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Settings.SPEC, "chronicle-common.toml");
        Wire.register(); MinecraftForge.EVENT_BUS.register(new ServerEvents());
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(dev.chronicle.compat.GunBridge::install));
    }
}
