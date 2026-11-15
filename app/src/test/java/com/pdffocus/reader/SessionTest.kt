package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class SessionTest {
    @Test fun sessionSurvivesRestartAndNewDocumentClearsIt(){val c=RuntimeEnvironment.getApplication();val s=SessionStore(c);s.clear();s.key="synthetic-document-A";val z=Zone(7,Box(.1f,.2f,.3f,.8f),Kind.TEXT);s.put(4,PageSession(listOf(z),listOf(7),3));val restored=SessionStore(c);assertEquals(4,restored.page);assertEquals(listOf(7),restored.get(4)!!.order);assertEquals(z.box,restored.get(4)!!.zones[0].box);restored.key="synthetic-document-B";assertNull(restored.get(4));assertEquals(0,restored.page)}
    @Test fun deletedSessionIsNotRestored(){val s=SessionStore(RuntimeEnvironment.getApplication());s.key="demo";s.clear();assertNull(SessionStore(RuntimeEnvironment.getApplication()).get(0))}
}
