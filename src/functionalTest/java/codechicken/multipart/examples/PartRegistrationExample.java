package codechicken.multipart.examples;

import net.minecraft.nbt.NBTTagCompound;

import codechicken.lib.data.MCDataInput;
import codechicken.multipart.MultiPartRegistry;
import codechicken.multipart.MultiPartRegistry.IPartFactory2;
import codechicken.multipart.TMultiPart;

/** Compiling example for docs/api/PART_REGISTRATION.md; geometry and gameplay callbacks belong to the real part. */
public final class PartRegistrationExample implements IPartFactory2 {

    public static final String PART_TYPE = "yourmod:part";

    /** Call from the mod's common preInit/init handler, on both sides. */
    public static void register() {
        MultiPartRegistry.registerPartFactory(new PartRegistrationExample(), PART_TYPE);
    }

    @Override
    public TMultiPart createPart(String name, NBTTagCompound nbt) {
        return new ExamplePart();
    }

    @Override
    public TMultiPart createPart(String name, MCDataInput packet) {
        return new ExamplePart();
    }

    private static final class ExamplePart extends TMultiPart {

        @Override
        public String getType() {
            return PART_TYPE;
        }
    }
}
