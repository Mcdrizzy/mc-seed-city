package net.tabor.seedcity.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.tabor.seedcity.SeedCity;

/** The five mobs are registered here. Phase 1 ships the Builder. */
public final class SeedCityEntities {
	public static EntityType<BuilderEntity> BUILDER;

	private SeedCityEntities() {
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SeedCity.id("builder"));
		BUILDER = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(BuilderEntity::new, MobCategory.MISC)
						.sized(0.6F, 0.7F)
						.clientTrackingRange(10)
						.build(key));
		FabricDefaultAttributeRegistry.register(BUILDER, BuilderEntity.createAttributes());
	}
}
