package com.fastasyncworldedit.bukkit.adapter;

import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.item.ItemTypes;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class CachedBukkitAdapter implements IBukkitAdapter {

    private Map<Material, Integer> itemTypesMap;
    private Map<Material, Integer> blockTypesMap;

    private boolean init() {
        if (itemTypesMap == null) {
            itemTypesMap = new HashMap<>();
            blockTypesMap = new HashMap<>();
            Material[] materials = Material.values();
            for (Material material : materials) {
                if (material.isLegacy()) {
                    continue;
                }
                NamespacedKey key = material.getKey();
                String id = key.getNamespace() + ":" + key.getKey();
                if (material.isBlock()) {
                    blockTypesMap.put(material, BlockTypes.get(id).getInternalId());
                }
                if (material.isItem()) {
                    itemTypesMap.put(material, ItemTypes.get(id).getInternalId());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Converts a Material to a ItemType.
     *
     * @param material The material
     * @return The itemtype
     */
    @Override
    public ItemType asItemType(Material material) {
        ItemType itemType;
        try {
            Integer id = itemTypesMap.get(material);
            itemType = id != null ? ItemTypes.get(id) : null;
        } catch (NullPointerException e) {
            if (init()) {
                return asItemType(material);
            }
            itemType = ItemTypes.get(itemTypesMap.get(material));
        }
        if (itemType == null) {
            if (ItemType.REGISTRY.get(material.key().asString()) == null) {
                itemType = new ItemType(material.key().asString());
                ItemType.REGISTRY.register(material.key().asString(), itemType);
            } else {
                itemType = ItemType.REGISTRY.get(material.key().asString());
            }
        }
        return itemType;
    }

    @Override
    public BlockType asBlockType(Material material) {
        try {
            Integer id = blockTypesMap.get(material);
            return id != null ? BlockTypesCache.values[id] : null;
        } catch (NullPointerException e) {
            if (init()) {
                return asBlockType(material);
            }
            throw e;
        }
    }

    /**
     * Create a WorldEdit BlockStateHolder from a Bukkit BlockData.
     *
     * @param blockData The Bukkit BlockData
     * @return The WorldEdit BlockState
     */
    @Override
    public BlockState adapt(BlockData blockData) {
        try {
            checkNotNull(blockData);
            Material material = blockData.getMaterial();
            Integer internalId = blockTypesMap.get(material);
            if (internalId == null) {
                return null;
            }
            BlockType type = BlockTypes.getFromStateId(internalId);
            List<? extends Property> propList = type.getProperties();
            if (propList.isEmpty()) {
                return type.getDefaultState();
            }
            String properties = blockData.getAsString();
            return BlockState.get(type, properties, type.getDefaultState());
        } catch (NullPointerException e) {
            if (init()) {
                return adapt(blockData);
            }
            throw e;
        }
    }

    protected abstract int[] getIbdToOrdinal();

    protected abstract int[] getOrdinalToIbdID();

}
