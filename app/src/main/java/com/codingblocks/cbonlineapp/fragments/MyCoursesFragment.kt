package com.codingblocks.cbonlineapp.fragments


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.Utils.retrofitCallback
import com.codingblocks.cbonlineapp.adapters.CourseDataAdapter
import com.codingblocks.cbonlineapp.database.*
import com.codingblocks.cbonlineapp.ui.AllCourseFragmentUi
import com.codingblocks.onlineapi.Clients
import com.codingblocks.onlineapi.models.MyCourse
import com.ethanhua.skeleton.Skeleton
import com.ethanhua.skeleton.SkeletonScreen
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.ctx
import kotlin.concurrent.thread


class MyCoursesFragment : Fragment(), AnkoLogger {

    val ui = AllCourseFragmentUi<Fragment>()
    private lateinit var courseDataAdapter: CourseDataAdapter
    private lateinit var skeletonScreen: SkeletonScreen

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(context!!)
    }

    private val courseDao by lazy {
        database.courseDao()
    }
    private val courseWithInstructorDao by lazy {
        database.courseWithInstructorDao()
    }
    private val instructorDao by lazy {
        database.instructorDao()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ui.createView(AnkoContext.create(ctx, this))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui.titleText.text = "My Courses"
        courseDataAdapter = CourseDataAdapter(ArrayList(), activity!!, courseWithInstructorDao, "myCourses")

        ui.rvCourses.layoutManager = LinearLayoutManager(ctx)
        ui.rvCourses.adapter = courseDataAdapter

        skeletonScreen = Skeleton.bind(ui.rvCourses)
                .adapter(courseDataAdapter)
                .shimmer(true)
                .angle(20)
                .frozen(true)
                .duration(1200)
                .count(4)
                .load(R.layout.item_skeleton_course_card)
                .show()
        courseDao.getMyCourses().observe(this, Observer<List<Course>> {
            courseDataAdapter.setData(it as ArrayList<Course>)

        })
        fetchAllCourses()
    }

    private fun fetchAllCourses() {

        Clients.onlineV2JsonApi.getMyCourses().enqueue(retrofitCallback { t, resp ->
            skeletonScreen.hide()
            resp?.body()?.let {
                info { it.toString() }
                for (myCourses in it) {

                    //Add Course Progress to Course Object
                    Clients.api.getMyCourseProgress(myCourses.run_attempts?.get(0)?.id.toString()).enqueue(retrofitCallback { t, progressResponse ->
                        progressResponse?.body().let { map ->
                            val progress = map!!["percent"] as Double
                            val course = myCourses.course?.run {
                                Course(
                                        id ?: "",
                                        title ?: "",
                                        subtitle ?: "",
                                        logo?.replace(".svg", ".png") ?: "",
                                        summary ?: "",
                                        promoVideo ?: "",
                                        difficulty ?: "",
                                        reviewCount ?: 0,
                                        rating ?: 0f,
                                        slug ?: "",
                                        coverImage?.replace(".svg", ".png") ?: "",
                                        myCourses.run_attempts?.get(0)?.id ?: "",
                                        updatedAt,
                                        progress,
                                        myCourses.description ?: "",
                                        CourseRun(myCourses.id
                                                ?: "", myCourses.run_attempts?.get(0)?.id ?: "",
                                                myCourses.name ?: "", myCourses.description ?: "",
                                                myCourses.start
                                                        ?: "", myCourses.run_attempts!![0].end
                                                ?: "",
                                                myCourses.price ?: "", myCourses.mrp ?: "",
                                                id ?: "", myCourses.updatedAt ?: ""

                                        )
                                )
                            }
                            doAsync {
                                val updateCourse = courseDao.getCourse(course?.id ?: "")
                                if (updateCourse == null) {
                                    courseDao.insert(course!!)
                                } else if (updateCourse.attempt_id == "" || updateCourse.progress != course?.progress) {
                                    info { "updateCourse is happening" }
                                    courseDao.update(course!!)
                                }
                                //fetch CourseInstructors
                                myCourses.course?.instructors?.forEachIndexed { _, it ->
                                    Clients.onlineV2JsonApi.instructorsById(it.id!!).enqueue(retrofitCallback { _, response ->

                                        response?.body().let { instructor ->
                                            thread {
                                                instructorDao.insert(Instructor(instructor?.id
                                                        ?: "", instructor?.name ?: "",
                                                        instructor?.description
                                                                ?: "", instructor?.photo ?: "",
                                                        "", myCourses.run_attempts!![0].id!!, myCourses.course!!.id))
                                                Log.e("TAG", "ID : ${instructor?.id}  Name : ${instructor?.name}")
                                                insertCourseAndInstructor(myCourses.course!!, instructor!!)
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    })
                }
            }
        })
    }

    private fun insertCourseAndInstructor(course: MyCourse, instructor: com.codingblocks.onlineapi.models.Instructor) {

        thread {
            try {
                courseWithInstructorDao.insert(CourseWithInstructor(course.id!!, instructor.id!!))
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("CRASH", "COURSE ID : ${course.id.toString()}")
                Log.e("CRASH", "INSTRUCTOR ID : ${instructor.id.toString()}")
            }
        }
    }

}
