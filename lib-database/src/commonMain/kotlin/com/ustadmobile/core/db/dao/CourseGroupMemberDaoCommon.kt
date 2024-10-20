package com.ustadmobile.core.db.dao

import com.ustadmobile.core.db.PermissionFlags
import com.ustadmobile.lib.db.entities.ClazzEnrolment

object CourseGroupMemberDaoCommon  {

    /**
     * If the courseGroupSet does not exist yet (e.g. edit screen with new entity) then we rely on
     * the clazzUid param to find students
     * If the courseGroupSet does exist then the screen will probably not receive the clazzuid
     * parameter and the query must rely on the entity of the coursegroupset itself.
     */
    const val CASE_CLAZZ_UID_WHEN_NOT_ZERO_USEIT_ELSE_LOOKUP_CGS_UID_SQL = """
        CASE(:clazzUid)
                         WHEN 0 THEN 
                                (SELECT CourseGroupSet.cgsClazzUid
                                   FROM CourseGroupSet
                                  WHERE CourseGroupSet.cgsUid = :cgsUid)
                         ELSE :clazzUid
                     END
    """

    //language=RoomSql
    const val FIND_BY_COURSEGROUPSET_AND_CLAZZ_SQL = """
        --First get a list of all enrolments - this may contains duplicates for students who leave and re-enrol
        WITH AllEnrollmentsAndActiveStatus(enrolledPersonUid, isActive) AS 
             (SELECT ClazzEnrolment.clazzEnrolmentPersonUid AS enrolledPersonUid,
                     (:time BETWEEN ClazzEnrolment.clazzEnrolmentDateJoined AND ClazzEnrolment.clazzEnrolmentDateLeft) AS isActive
                FROM ClazzEnrolment
               WHERE ClazzEnrolment.clazzEnrolmentClazzUid = $CASE_CLAZZ_UID_WHEN_NOT_ZERO_USEIT_ELSE_LOOKUP_CGS_UID_SQL
                 AND ClazzEnrolment.clazzEnrolmentRole = ${ClazzEnrolment.ROLE_STUDENT}),
        --Consolidate and removes any duplicates
             EnrolledStudentPersonUids(enrolledPersonUid, isActive) AS
             (SELECT DISTINCT AllEnrollmentsAndActiveStatus.enrolledPersonUid,
                     (SELECT CAST(AllEnrollmentsInner.isActive AS INTEGER)
                        FROM AllEnrollmentsAndActiveStatus AllEnrollmentsInner
                       WHERE AllEnrollmentsInner.enrolledPersonUid = AllEnrollmentsAndActiveStatus.enrolledPersonUid
                    ORDER BY AllEnrollmentsInner.isActive DESC
                       LIMIT 1) AS isActive
                FROM AllEnrollmentsAndActiveStatus)
        
        -- Now create a list with each students name, the coursegroupmember object if any and active status        
        SELECT (Person.firstNames || ' ' || Person.lastName) AS name,
               Person.personUid,
               CourseGroupMember.*,
               PersonPicture.*,
               EnrolledStudentPersonUids.isActive AS enrolmentIsActive,
               PersonPicture.personPictureThumbnailUri AS pictureUri
          FROM EnrolledStudentPersonUids
               JOIN Person
                    ON Person.personUid = EnrolledStudentPersonUids.enrolledPersonUid 
               LEFT JOIN PersonPicture
                         ON PersonPicture.personPictureUid = Person.personUid  
               -- LEFT JOIN will use the most recent member in case of duplicate assignments eg if      
               LEFT JOIN CourseGroupMember
                         ON CourseGroupMember.cgmUid = 
                            (SELECT CourseGroupMember.cgmUid
                               FROM CourseGroupMember
                              WHERE CourseGroupMember.cgmPersonUid = EnrolledStudentPersonUids.enrolledPersonUid
                                AND CourseGroupMember.cgmSetUid = :cgsUid 
                           ORDER BY CourseGroupMember.cgmLct DESC        
                              LIMIT 1)
         WHERE (:activeFilter = 0 OR :activeFilter = EnrolledStudentPersonUids.isActive)  
               /* 
                * Begin permission check -  must have course view members permission, or active 
                * user must be in the same group 
                */ 
            AND (
                    (${CoursePermissionDaoCommon.PERSON_COURSE_PERMISSION_CLAUSE_FOR_ACCOUNT_PERSON_UID_AND_CLAZZUID_SQL_PT1} ${PermissionFlags.PERSON_VIEW}
                     ${CoursePermissionDaoCommon.PERSON_COURSE_PERMISSION_CLAUSE_FOR_ACCOUNT_PERSON_UID_AND_CLAZZUID_SQL_PT2} ${PermissionFlags.PERSON_VIEW}
                     ${CoursePermissionDaoCommon.PERSON_COURSE_PERMISSION_CLAUSE_FOR_ACCOUNT_PERSON_UID_AND_CLAZZUID_SQL_PT3})
                  OR EXISTS(
                     SELECT 1
                       FROM CourseGroupMember _CourseGroupMemberForActivePerson
                      WHERE _CourseGroupMemberForActivePerson.cgmPersonUid = :accountPersonUid
                        AND _CourseGroupMemberForActivePerson.cgmGroupNumber = CourseGroupMember.cgmGroupNumber)     
                 )
      ORDER BY Person.firstNames, Person.lastName ASC
    """

}