package codechicken.microblock;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/** Presents a microblock's material at its position without changing the surrounding world. */
final class MaterialBlockAccess implements IBlockAccess {

    private final IBlockAccess world;
    private final Block block;
    private final int meta;
    private final int x, y, z;

    MaterialBlockAccess(IBlockAccess world, Block block, int meta, int x, int y, int z) {
        this.world = world;
        this.block = block;
        this.meta = meta;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private boolean isMaterial(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return isMaterial(x, y, z) ? block : world.getBlock(x, y, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return isMaterial(x, y, z) ? meta : world.getBlockMetadata(x, y, z);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return isMaterial(x, y, z) ? null : world.getTileEntity(x, y, z);
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int light) {
        return world.getLightBrightnessForSkyBlocks(x, y, z, light);
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return isMaterial(x, y, z) ? block.isAir(this, x, y, z) : world.isAirBlock(x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return world.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getHeight() {
        return world.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return world.extendedLevelsInChunkCache();
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return world.isBlockProvidingPowerTo(x, y, z, side);
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) {
        return isMaterial(x, y, z) ? block.isSideSolid(this, x, y, z, side)
                : world.isSideSolid(x, y, z, side, fallback);
    }
}
