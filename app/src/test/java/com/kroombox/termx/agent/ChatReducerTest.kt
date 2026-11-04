package com.kroombox.termx.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {
    private fun ev(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun streamsTextAndClosesOnDone() {
        val r = ChatReducer()
        r.apply(ev("""{"t":"user","text":"hi","n":0,"at":1}"""))
        r.apply(ev("""{"t":"status","s":"working","n":1}"""))
        r.apply(ev("""{"t":"text_start","id":"a","n":2}"""))
        r.apply(ev("""{"t":"delta","id":"a","d":"Hel","n":3}"""))
        r.apply(ev("""{"t":"delta","id":"a","d":"lo","n":4}"""))
        var s = r.snapshot()
        assertTrue(s.working)
        assertEquals(TextItem("a", "Hello", true), s.items[1])
        r.apply(ev("""{"t":"done","ok":true,"cost":0.5,"in":10,"out":3,"ms":1200,"model":"m","n":5}"""))
        r.apply(ev("""{"t":"status","s":"idle","n":6}"""))
        s = r.snapshot()
        assertFalse(s.working)
        assertEquals(TextItem("a", "Hello", false), s.items[1])
        assertTrue(s.items.last() is TurnItem)
        assertEquals(0.5, s.cost, 1e-9)
        assertEquals(13L, s.tokensIn + s.tokensOut)
    }

    @Test fun replayedEventsAreIgnored() {
        val r = ChatReducer()
        assertTrue(r.apply(ev("""{"t":"note","msg":"x","n":0}""")))
        assertFalse(r.apply(ev("""{"t":"note","msg":"x","n":0}""")))
        assertFalse(r.apply(ev("""{"t":"hb"}""")))
        assertEquals(1, r.snapshot().items.size)
        assertEquals(0, r.lastSeq)
    }

    @Test fun optimisticMessageReplacedByServerEcho() {
        val r = ChatReducer()
        r.optimistic("fix it")
        assertTrue(r.snapshot().items.single().key.startsWith("local"))
        r.apply(ev("""{"t":"user","text":"fix it","n":7,"at":5}"""))
        val items = r.snapshot().items
        assertEquals(1, items.size)
        assertEquals("u7", items.single().key)
    }

    @Test fun permissionsAndToolsSettleWhenTurnEnds() {
        val r = ChatReducer()
        r.apply(ev("""{"t":"tool","id":"t1","name":"Bash","input":{"command":"ls"},"n":0}"""))
        r.apply(ev("""{"t":"perm","rid":"9","tool":"Bash","desc":"","input":{"command":"ls"},"always":true,"n":1}"""))
        var s = r.snapshot()
        assertEquals(1, s.pendingPermissions)
        assertTrue((s.items[0] as ToolItem).running)
        r.apply(ev("""{"t":"done","ok":false,"error":"Stopped.","n":2}"""))
        s = r.snapshot()
        assertEquals(0, s.pendingPermissions)
        assertEquals(false, (s.items[1] as PermItem).allowed)
        assertEquals(false, (s.items[0] as ToolItem).ok)
        assertEquals("Stopped.", (s.items[2] as TurnItem).error)
    }

    @Test fun permDoneMarksAnswer() {
        val r = ChatReducer()
        r.apply(ev("""{"t":"perm","rid":"3","tool":"Write","input":{},"n":0}"""))
        r.apply(ev("""{"t":"perm_done","rid":"3","allow":true,"n":1}"""))
        assertEquals(true, (r.snapshot().items.single() as PermItem).allowed)
    }

    @Test fun limitsModelsAndRetry() {
        val r = ChatReducer()
        r.apply(ev("""{"t":"limits","windows":{"five_hour":{"used":0.1,"reset":100,"status":"allowed"}},"n":0}"""))
        r.apply(ev("""{"t":"models","list":[{"id":"a/b","name":"B"}],"current":"a/b","n":1}"""))
        r.apply(ev("""{"t":"retry","msg":"Reconnecting 1/5","n":2}"""))
        var s = r.snapshot()
        assertEquals("five_hour", s.limits.single().key)
        assertEquals(0.1, s.limits.single().used!!, 1e-9)
        assertEquals(100L, s.limits.single().resetEpochSec)
        assertEquals("a/b", s.model)
        assertEquals("Reconnecting 1/5", s.retry)
        r.apply(ev("""{"t":"text_start","id":"x","n":3}"""))
        s = r.snapshot()
        assertNull(s.retry)
    }

    @Test fun emptyTextBlockIsDropped() {
        val r = ChatReducer()
        r.apply(ev("""{"t":"text_start","id":"x","n":0}"""))
        r.apply(ev("""{"t":"text_end","id":"x","n":1}"""))
        assertTrue(r.snapshot().items.isEmpty())
    }

    @Test fun screenUpdatesInPlaceAndMirrorEnds() {
        val r = ChatReducer()
        r.apply(ev("""{"t":"screen","text":"one","n":0}"""))
        r.apply(ev("""{"t":"note","msg":"x","n":1}"""))
        r.apply(ev("""{"t":"screen","text":"two","n":2}"""))
        val s = r.snapshot()
        assertEquals(2, s.items.size)
        assertEquals(ScreenItem("screen", "two"), s.items.last())
        assertFalse(s.mirrorEnded)
        r.apply(ev("""{"t":"mirror_end","n":3}"""))
        assertTrue(r.snapshot().mirrorEnded)
    }

    @Test fun olderAndSyncedMarkers() {
        val r = ChatReducer()
        r.apply(kotlinx.serialization.json.Json.parseToJsonElement("""{"t":"older","turns":12}""").jsonObject)
        r.apply(kotlinx.serialization.json.Json.parseToJsonElement("""{"t":"user","text":"hi","n":40,"at":1}""").jsonObject)
        assertEquals(false, r.snapshot().synced)
        r.apply(kotlinx.serialization.json.Json.parseToJsonElement("""{"t":"synced"}""").jsonObject)
        val s = r.snapshot()
        assertEquals(12, s.olderTurns)
        assertEquals(true, s.synced)
        assertEquals(40, r.lastSeq)
        r.reset()
        assertEquals(false, r.snapshot().synced)
    }

    @Test fun modelsCarryDescriptionAndEfforts() {
        val r = ChatReducer()
        r.apply(ev("""{"t":"models","list":[{"id":"opus","name":"Opus 5.5","desc":"For complex work","efforts":["low","high","max"]},{"id":"claude-haiku-4-5","name":"Haiku 4.5"}],"current":"opus","n":0}"""))
        r.apply(ev("""{"t":"efforts","list":[{"id":"low","name":"low"},{"id":"max","name":"max"}],"current":"high","n":1}"""))
        var s = r.snapshot()
        assertEquals(Choice("opus", "Opus 5.5", "For complex work", listOf("low", "high", "max")), s.models[0])
        assertEquals(emptyList<String>(), s.models[1].efforts)
        assertEquals("opus", s.model)
        assertEquals("high", s.effort)
        assertEquals(2, s.efforts.size)
        r.apply(ev("""{"t":"effort","effort":"","n":2}"""))
        s = r.snapshot()
        assertEquals("", s.effort)
    }
}
