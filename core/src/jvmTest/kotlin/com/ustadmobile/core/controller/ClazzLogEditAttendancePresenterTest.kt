package com.ustadmobile.core.controller

import com.nhaarman.mockitokotlin2.*
import com.ustadmobile.core.db.waitForLiveData
import com.ustadmobile.core.util.SystemImplRule
import com.ustadmobile.core.util.UmAppDatabaseClientRule
import com.ustadmobile.core.view.ClazzLogEditAttendanceView
import com.ustadmobile.core.view.UstadView
import com.ustadmobile.door.DoorLifecycleOwner
import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.door.DoorMutableLiveData
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.util.test.ext.insertTestClazzAndMembers
import com.ustadmobile.util.test.ext.insertTestClazzLog
import com.ustadmobile.util.test.ext.insertTestRecordsForClazzLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ClazzLogEditAttendancePresenterTest {

    private lateinit var mockView: ClazzLogEditAttendanceView

    private lateinit var context: Any

    private lateinit var mockLifecycleOwner: DoorLifecycleOwner

    @JvmField
    @Rule
    var systemImplRule = SystemImplRule()

    @JvmField
    @Rule
    var clientDbRule = UmAppDatabaseClientRule(useDbAsRepo = true)

    @Before
    fun setup() {
        mockView = mock { }
        mockLifecycleOwner = mock { }
        context = Any()
    }

    @Test
    fun givenExistingClazzWithStudentsAndNoAttendanceLogsYet_whenLoadedFromDbAndAttendanceSet_thenShouldSetListWithAllMembersAndSaveToDatabase() {
        val testClazzAndMembers = runBlocking { clientDbRule.db.insertTestClazzAndMembers(5, 1) }
        val testClazzLog = runBlocking { clientDbRule.db.clazzLogDao.insertTestClazzLog(testClazzAndMembers.clazz.clazzUid )}

        val presenter = ClazzLogEditAttendancePresenter(context,
                mapOf(UstadView.ARG_ENTITY_UID to testClazzLog.clazzLogUid.toString()), mockView,
                mockLifecycleOwner, systemImplRule.systemImpl, clientDbRule.db, clientDbRule.repo,
                clientDbRule.accountLiveData)
        presenter.onCreate(null)

        //wait for the view to finish loading
        val entityVal = nullableArgumentCaptor<ClazzLog>().run {
            verify(mockView, timeout(5000).atLeastOnce()).entity = capture()
            firstValue
        }

        presenter.handleClickMarkAll(ClazzLogAttendanceRecord.STATUS_ATTENDED)

        presenter.handleClickSave(entityVal!!)

        nullableArgumentCaptor<DoorMutableLiveData<List<ClazzLogAttendanceRecordWithPerson>>>().apply {
            verify(mockView, timeout(5000).atLeastOnce()).clazzLogAttendanceRecordList = capture()
            assertEquals("Got expected number of class members", 5,
                    testClazzAndMembers.studentList.size)
        }
    }

    @Test
    fun givenExistingClazWithStudentsAndAttendanceLogsInDb_whenLoadedFromDb_thenShouldSetListWithAllMembers() {
        val testClazzAndMembers = runBlocking { clientDbRule.db.insertTestClazzAndMembers(5, 1) }
        val testClazzLog = runBlocking { clientDbRule.db.clazzLogDao.insertTestClazzLog(testClazzAndMembers.clazz.clazzUid )}
        val testAttendanceLogs = runBlocking {
            clientDbRule.db.clazzLogAttendanceRecordDao.insertTestRecordsForClazzLog(testClazzLog,
                    testClazzAndMembers.studentList)
        }


        val presenter = ClazzLogEditAttendancePresenter(context,
                mapOf(UstadView.ARG_ENTITY_UID to testClazzLog.clazzLogUid.toString()), mockView,
                mockLifecycleOwner, systemImplRule.systemImpl, clientDbRule.db, clientDbRule.repo,
                clientDbRule.accountLiveData)
        presenter.onCreate(null)

        nullableArgumentCaptor<DoorMutableLiveData<List<ClazzLogAttendanceRecordWithPerson>>>().apply {
            verify(mockView, timeout(5000)).clazzLogAttendanceRecordList = capture()
            assertEquals("Got expected number of class members", 5,
                    testClazzAndMembers.studentList.size)
        }
    }

    @Test
    fun givenExistingClazzWithStudents_whenClickMarkAllThenSavedCalled_thenShouldSetAllAndSaveToDatabase() {
        val testClazzAndMembers = runBlocking { clientDbRule.db.insertTestClazzAndMembers(5, 1) }
        val testClazzLog = runBlocking { clientDbRule.db.clazzLogDao.insertTestClazzLog(testClazzAndMembers.clazz.clazzUid )}

        val presenter = ClazzLogEditAttendancePresenter(context,
                mapOf(UstadView.ARG_ENTITY_UID to testClazzLog.clazzLogUid.toString()), mockView,
                mockLifecycleOwner, systemImplRule.systemImpl, clientDbRule.db, clientDbRule.repo,
                clientDbRule.accountLiveData)
        presenter.onCreate(null)

        verify(mockView, timeout(5000)).clazzLogAttendanceRecordList = any()

        nullableArgumentCaptor<DoorMutableLiveData<List<ClazzLogAttendanceRecordWithPerson>>>().apply {
            verify(mockView, timeout(5000)).clazzLogAttendanceRecordList = capture()
            runBlocking {
                waitForLiveData(firstValue as DoorLiveData<List<ClazzLogAttendanceRecordWithPerson>>, 5000) {
                    it.size == testClazzAndMembers.studentList.size
                }

                presenter.handleClickMarkAll(ClazzLogAttendanceRecord.STATUS_ATTENDED)

                waitForLiveData(firstValue as DoorLiveData<List<ClazzLogAttendanceRecordWithPerson>>, 5000) {
                    it.size == testClazzAndMembers.studentList.size &&
                            it.all { it.attendanceStatus == ClazzLogAttendanceRecord.STATUS_ATTENDED }
                }

                assertEquals("Received the expected number of attendance records",
                        testClazzAndMembers.studentList.size, firstValue!!.getValue()!!.size)

                assertTrue("Last value marks all as attended",
                    firstValue!!.getValue()!!.all { it.attendanceStatus == ClazzLogAttendanceRecord.STATUS_ATTENDED })
            }
        }
    }

}