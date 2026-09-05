package codechicken.multipart.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.packet.PacketCustom;
import codechicken.multipart.MultiPartRegistry;
import codechicken.multipart.MultiPartRegistry$;
import codechicken.multipart.MultiPartRegistry.IPartFactory;
import codechicken.multipart.MultiPartRegistry.IPartFactory2;
import codechicken.multipart.TMultiPart;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.runtime.AbstractFunction2;

class PartRegistrationFunctionalTest {

    private static final List<BiConsumer<IPartFactory2, String[]>> ENTRIES = Arrays.asList(
            (factory, types) -> MultiPartRegistry.registerParts(factory, types),
            (factory, types) -> MultiPartRegistry$.MODULE$.registerParts(factory, types),
            (factory, types) -> MultiPartRegistry.registerParts(factory, seq(types)),
            (factory, types) -> MultiPartRegistry$.MODULE$.registerParts(factory, seq(types)));
    private static final IPartFactory2 FACTORY = new IPartFactory2() {

        @Override
        public TMultiPart createPart(String name, NBTTagCompound nbt) {
            factoryCalls++;
            return new Part(name, false, nbt, null, -1);
        }

        @Override
        public TMultiPart createPart(String name, MCDataInput packet) {
            factoryCalls++;
            return new Part(name, true, null, packet, packet == null ? -1 : packet.readUByte());
        }
    };
    private static int factoryCalls;
    private static int callsAfterRegistration;
    private static ModContainer owner;
    private static IllegalStateException duplicateFailure;
    private static NullPointerException nullArrayFailure;

    static void registerDuringInit() {
        owner = Loader.instance().activeModContainer();
        for (int i = 0; i < ENTRIES.size(); i++) {
            String[] types = { type(i) };
            ENTRIES.get(i).accept(FACTORY, types);
            types[0] = "test:registration_mutated_array";
        }
        IPartFactory oldFactory = (name, client) -> new Part(name, client, null, null, -1);
        MultiPartRegistry.registerParts(oldFactory, "test:registration_boolean_static");
        MultiPartRegistry$.MODULE$.registerParts(oldFactory, "test:registration_boolean_companion");
        AbstractFunction2<String, Object, TMultiPart> function = new AbstractFunction2<String, Object, TMultiPart>() {

            @Override
            public TMultiPart apply(String name, Object client) {
                return oldFactory.createPart(name, (Boolean) client);
            }
        };
        MultiPartRegistry.registerParts(function, seq("test:registration_function_static"));
        MultiPartRegistry$.MODULE$.registerParts(function, seq("test:registration_function_companion"));
        for (int i = 0; i < ENTRIES.size(); i++) {
            final int entry = i;
            duplicateFailure = assertThrows(
                    IllegalStateException.class,
                    () -> ENTRIES.get(entry).accept(
                            FACTORY,
                            new String[] { type(entry) + "_prefix", type(entry), type(entry) + "_tail" }));
        }
        nullArrayFailure = assertThrows(
                NullPointerException.class,
                () -> MultiPartRegistry.registerParts(FACTORY, (String[]) null));
        callsAfterRegistration = factoryCalls;
    }

    @Test
    void registrationIsLazyOwnedByTheActiveModAndDoesNotRetainTheArray() {
        assertEquals("ForgeMultipartTests", owner.getModId());
        assertEquals(0, callsAfterRegistration);
        assertTrue(MultiPartRegistry.required());
        assertTrue(MultiPartRegistry.loaded());
        for (int i = 0; i < ENTRIES.size(); i++) {
            assertSame(owner, MultiPartRegistry.getModContainer(type(i)));
        }
        assertThrows(
                NoSuchElementException.class,
                () -> MultiPartRegistry.getModContainer("test:registration_mutated_array"));
    }

    @Test
    void typedFactoriesKeepNbtIdentityAndTheSharedPacketCursor() {
        for (int i = 0; i < ENTRIES.size(); i++) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setInteger("value", 23);
            Part server = (Part) MultiPartRegistry.loadPart(type(i), nbt);
            assertFalse(server.client);
            assertSame(nbt, server.nbt);
            assertEquals(0, server.loads);
            server.load(nbt);
            assertEquals(1, server.loads);
            assertEquals(23, server.value);
            assertNotSame(server, MultiPartRegistry.loadPart(type(i), nbt));
            PacketCustom packet = packet(server);
            Part client = (Part) MultiPartRegistry.readPart(packet);
            assertTrue(client.client);
            assertSame(packet, client.packet);
            assertEquals(17, client.discriminator);
            assertEquals(0, client.reads);
            client.readDesc(packet);
            assertEquals(1, client.reads);
            assertEquals(23, client.value);
            assertEquals(0, packet.getByteBuf().readableBytes());
            assertNull(server.tile());
            assertNull(client.tile());
        }
    }

    @Test
    void booleanAndScalaFunctionAdaptersKeepTheSideAndLeavePayloadConsumptionToThePart() {
        for (String suffix : Arrays
                .asList("boolean_static", "boolean_companion", "function_static", "function_companion")) {
            String name = "test:registration_" + suffix;
            Part server = (Part) MultiPartRegistry.loadPart(name, new NBTTagCompound());
            assertFalse(server.client);
            PacketCustom packet = packet(server);
            Part client = (Part) MultiPartRegistry.readPart(packet);
            assertTrue(client.client);
            assertEquals(name, client.getType());
            assertEquals(17, packet.readUByte(), "Legacy adapters must not consume the description payload");
            assertEquals(23, packet.readUByte());
        }
    }

    @Test
    void duplicateRegistrationKeepsThePrefixAndClosedRegistryGuardsKeepTheirOrdering() {
        assertTrue(duplicateFailure.getMessage().contains("already registered"));
        assertTrue(nullArrayFailure instanceof NullPointerException);
        for (int i = 0; i < ENTRIES.size(); i++) {
            assertSame(owner, MultiPartRegistry.getModContainer(type(i) + "_prefix"));
            final String tail = type(i) + "_tail";
            assertThrows(NoSuchElementException.class, () -> MultiPartRegistry.getModContainer(tail));
            final BiConsumer<IPartFactory2, String[]> entry = ENTRIES.get(i);
            assertEquals(
                    "Parts must be registered in the init methods.",
                    assertThrows(IllegalStateException.class, () -> entry.accept(null, new String[0])).getMessage());
        }
        assertThrows(IllegalStateException.class, () -> MultiPartRegistry.registerParts(FACTORY, (String[]) null));
        assertThrows(NullPointerException.class, () -> MultiPartRegistry.registerParts(FACTORY, (Seq<String>) null));
        assertThrows(
                NullPointerException.class,
                () -> MultiPartRegistry$.MODULE$.registerParts(FACTORY, (Seq<String>) null));
    }

    private static String type(int index) {
        return "test:registration_" + index;
    }

    private static Seq<String> seq(String... types) {
        return JavaConversions.asScalaBuffer(Arrays.asList(types)).toList();
    }

    private static PacketCustom packet(TMultiPart part) {
        PacketCustom outgoing = new PacketCustom("test", 1);
        MultiPartRegistry.writePartID(outgoing, part);
        outgoing.writeByte(17);
        outgoing.writeByte(23);
        return new PacketCustom(outgoing.getByteBuf().copy());
    }

    private static final class Part extends TMultiPart {

        private final String name;
        private final boolean client;
        private final NBTTagCompound nbt;
        private final MCDataInput packet;
        private final int discriminator;
        private int loads;
        private int reads;
        private int value;

        private Part(String name, boolean client, NBTTagCompound nbt, MCDataInput packet, int discriminator) {
            this.name = name;
            this.client = client;
            this.nbt = nbt;
            this.packet = packet;
            this.discriminator = discriminator;
        }

        @Override
        public String getType() {
            return name;
        }

        @Override
        public void load(NBTTagCompound tag) {
            loads++;
            value = tag.getInteger("value");
        }

        @Override
        public void readDesc(MCDataInput data) {
            reads++;
            value = data.readUByte();
        }
    }
}
