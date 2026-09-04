package codechicken.multipart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import scala.collection.JavaConversions;
import scala.collection.Seq;

/** Pair ordering, failures and the tile-level override hook behind collection occlusion queries. */
class TileMultipartOcclusionTest {

    @Test
    void legacyQueryChecksBothDirectionsInOrderAndStopsAtEitherRejection() {
        TileMultipart tile = new TileMultipart();
        List<String> calls = new ArrayList<>();
        Part first = new Part("first", calls);
        Part second = new Part("second", calls);
        Part candidate = new Part("candidate", calls);
        assertTrue(tile.occlusionTest(seq(first, second), candidate));
        assertEquals(
                Arrays.asList("first:candidate", "candidate:first", "second:candidate", "candidate:second"),
                calls);
        assertNull(first.tile(), "Unbound parts participate in geometry queries");
        calls.clear();
        first.accepts = false;
        assertFalse(tile.occlusionTest(seq(first, second), candidate));
        assertEquals(Arrays.asList("first:candidate"), calls);
        calls.clear();
        first.accepts = true;
        candidate.accepts = false;
        assertFalse(tile.occlusionTest(seq(first, second), candidate));
        assertEquals(Arrays.asList("first:candidate", "candidate:first"), calls);
    }

    @Test
    void legacyQueryRetainsLazyNullChecksAndOriginalCallbackFailures() {
        TileMultipart tile = new TileMultipart();
        List<String> calls = new ArrayList<>();
        Part existing = new Part("existing", calls);
        Part candidate = new Part("candidate", calls);
        assertTrue(tile.occlusionTest(seq(), null));
        assertThrows(NullPointerException.class, () -> tile.occlusionTest(null, candidate));
        existing.accepts = false;
        assertFalse(tile.occlusionTest(seq(existing, null), null));
        assertEquals(Arrays.asList("existing:null"), calls);
        existing.accepts = true;
        calls.clear();
        IllegalStateException failure = new IllegalStateException("candidate callback");
        candidate.failure = failure;
        assertSame(
                failure,
                assertThrows(IllegalStateException.class, () -> tile.occlusionTest(seq(existing, null), candidate)));
        assertEquals(Arrays.asList("existing:candidate", "candidate:existing"), calls);
    }

    @Test
    void replacementChecksKeepTheLegacyTileOverrideAndExcludeTheOutgoingPart() {
        List<String> calls = new ArrayList<>();
        List<Seq<TMultiPart>> inputs = new ArrayList<>();
        TileMultipart tile = new TileMultipart() {

            @Override
            public boolean occlusionTest(Seq<TMultiPart> parts, TMultiPart candidate) {
                inputs.add(parts);
                return false;
            }
        };
        Part outgoing = new Part("outgoing", calls);
        Part retained = new Part("retained", calls);
        Part candidate = new Part("candidate", calls);
        tile.partList_$eq(seq(outgoing, retained));
        assertFalse(tile.canReplacePart(outgoing, candidate));
        assertEquals(Arrays.asList(seq(retained)), inputs);
        assertTrue(calls.isEmpty(), "The override can reject without invoking pair callbacks");
        assertEquals(Arrays.asList(outgoing, retained), tile.jPartList());
    }

    private static Seq<TMultiPart> seq(TMultiPart... parts) {
        return JavaConversions.asScalaBuffer(Arrays.asList(parts)).toList();
    }

    private static class Part extends TMultiPart {

        private final String name;
        private final List<String> calls;
        private boolean accepts = true;
        private RuntimeException failure;
        private Runnable onTest;

        private Part(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public String getType() {
            return name;
        }

        @Override
        public boolean occlusionTest(TMultiPart other) {
            calls.add(name + ":" + (other == null ? "null" : other.getType()));
            if (onTest != null) {
                onTest.run();
            }
            if (failure != null) {
                throw failure;
            }
            return accepts;
        }
    }
}
