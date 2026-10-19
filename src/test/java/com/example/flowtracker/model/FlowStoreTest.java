package com.example.flowtracker.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.flowtracker.Fixtures.line;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saved lines, named flows, undo / redo and the Recycle Bin. */
class FlowStoreTest {

    private final FlowEntry a = line("A.java", "A", "a()", 1, "a();");
    private final FlowEntry b = line("A.java", "A", "a()", 2, "b();");
    private final FlowEntry c = line("A.java", "A", "a()", 3, "c();");

    private FlowStore storeWithABC() {
        FlowStore store = new FlowStore();
        store.add(a);
        store.add(b);
        store.add(c);
        return store;
    }

    @Test
    void addAllIsOneUndoStepAndKeepsOrder() {
        FlowStore store = new FlowStore();
        store.add(a);
        assertEquals(2, store.addAll(List.of(b, c, line("A.java", "A", "a()", 1, "dup")), false), "file + line saved already is skipped");
        assertEquals(List.of(a, b, c), store.getEntries());
        assertEquals("Add 2 lines", store.undoLabel());
        store.undo();
        assertEquals(List.of(a), store.getEntries());

        FlowStore top = new FlowStore();
        top.add(a);
        top.addAll(List.of(b, c), true);
        assertEquals(List.of(b, c, a), top.getEntries(), "added at the top in their own order");
    }

    @Test
    void deletedLinesGoToTheBinAndUndoTakesThemBack() {
        FlowStore store = storeWithABC();
        store.remove(List.of(b));
        assertEquals("Delete A.java:2", store.getRecycleBin().get(0).label);
        store.undo();
        assertTrue(store.getRecycleBin().isEmpty());
        assertEquals(List.of(a, b, c), store.getEntries());
        store.redo();
        assertEquals(1, store.getRecycleBin().size());
        assertEquals(1, store.restore(store.getRecycleBin().get(0)));
        assertEquals(List.of(a, b, c), store.getEntries(), "restored at its old place");
    }

    @Test
    void clearAndDeletedFlowsCanBeRestored() {
        FlowStore store = storeWithABC();
        store.clear();
        assertEquals("Clear all (3 lines)", store.getRecycleBin().get(0).label);

        store.newFlow("Checkout bug", List.of(line("B.java", "B", "b()", 5, "x();")));
        store.deleteFlow();
        FlowStore.BinItem flow = store.getRecycleBin().get(0);
        assertTrue(flow.wholeFlow);
        assertEquals("Checkout bug", flow.flowName);
        store.restore(flow);
        assertEquals("Checkout bug", store.getActiveFlowName());
        assertEquals(1, store.getEntries().size());

        store.emptyRecycleBin();
        assertTrue(store.getRecycleBin().isEmpty());
        store.purge(List.of());
    }

    @Test
    void flowsCanBeReorderedAndTheActiveOneStays() {
        FlowStore store = new FlowStore();
        store.add(a);
        store.newFlow("Flow 2", List.of(b));
        store.newFlow("Flow 3", List.of());
        store.switchFlow(1);
        store.moveFlow(1, 0);
        assertEquals(List.of("Flow 2", "Flow 1", "Flow 3"), store.getFlowNames());
        assertEquals("Flow 2", store.getActiveFlowName());
        store.moveFlow(2, 0);
        assertEquals(List.of("Flow 3", "Flow 2", "Flow 1"), store.getFlowNames());
        assertEquals("Flow 2", store.getActiveFlowName());
        assertEquals(List.of("Flow 3", "Flow 2", "Flow 1"), store.getAllFlows().stream().map(f -> f.name).toList(),
                "Copy All Flows uses this order");
        store.moveFlow(0, 9);
        assertEquals("Flow 3", store.getFlowNames().get(0), "out of range is ignored");
    }

    @Test
    void flowNamesKeepTheirPrefix() {
        FlowStore store = new FlowStore();
        store.renameFlow("Flow 1: Checkout bug");
        assertEquals("Flow 1: Checkout bug", store.getActiveFlowName());
        store.newFlow("Flow 1: Checkout bug", List.of());
        assertEquals("Flow 1: Checkout bug 2", store.getActiveFlowName(), "names are made unique");
        assertFalse(store.canUndo(), "a new flow starts with empty history");
    }
}
