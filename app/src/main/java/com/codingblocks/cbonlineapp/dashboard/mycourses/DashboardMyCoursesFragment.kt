package com.codingblocks.cbonlineapp.dashboard.mycourses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.analytics.AppCrashlyticsWrapper
import com.codingblocks.cbonlineapp.auth.LoginActivity
import com.codingblocks.cbonlineapp.commons.SheetAdapter
import com.codingblocks.cbonlineapp.commons.SheetItem
import com.codingblocks.cbonlineapp.dashboard.DashboardViewModel
import com.codingblocks.cbonlineapp.mycourse.MyCourseActivity
import com.codingblocks.cbonlineapp.util.COURSE_ID
import com.codingblocks.cbonlineapp.util.COURSE_NAME
import com.codingblocks.cbonlineapp.util.Components
import com.codingblocks.cbonlineapp.util.PreferenceHelper
import com.codingblocks.cbonlineapp.util.RUN_ATTEMPT_ID
import com.codingblocks.cbonlineapp.util.RUN_ID
import com.codingblocks.cbonlineapp.util.UNAUTHORIZED
import com.codingblocks.cbonlineapp.util.extensions.changeViewState
import com.codingblocks.cbonlineapp.util.extensions.observer
import com.codingblocks.cbonlineapp.util.extensions.setRv
import com.codingblocks.cbonlineapp.util.extensions.showEmptyView
import com.codingblocks.cbonlineapp.util.extensions.showSnackbar
import com.codingblocks.onlineapi.ErrorStatus
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.app_bar_dashboard.*
import kotlinx.android.synthetic.main.bottom_sheet_mycourses.view.*
import kotlinx.android.synthetic.main.fragment_dashboard_my_courses.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.singleTop
import org.jetbrains.anko.support.v4.intentFor
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class DashboardMyCoursesFragment : Fragment(), AnkoLogger {

    private val dialog by lazy { BottomSheetDialog(requireContext()) }
    private val imgs by lazy { resources.obtainTypedArray(R.array.course_type_img) }
    private val coursesType by lazy { resources.getStringArray(R.array.course_type) }
    private val viewModel by sharedViewModel<DashboardViewModel>()
    private val type = MutableLiveData<Int>()
    private val courseListAdapter = MyCourseListAdapter()
    private val sharedPrefs by inject<PreferenceHelper>()

    private val itemClickListener: ItemClickListener by lazy {
        object : ItemClickListener {

            override fun onClick(id: String, runId: String, runAttemptId: String, name: String) {
                startActivity(intentFor<MyCourseActivity>(
                    COURSE_ID to id,
                    RUN_ID to runId,
                    RUN_ATTEMPT_ID to runAttemptId,
                    COURSE_NAME to name
                ).singleTop())
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_dashboard_my_courses, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dashboardCourseShimmer.startShimmer()
//        type.value = viewModel.prefs.courseFilter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpBottomSheet()

        courseTypeTv.apply {
            val lastSelected = sharedPrefs.SP_COURSE_FILTER_TYPE
            text = coursesType[lastSelected]
            viewModel.courseFilter.postValue(coursesType[lastSelected])
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                requireContext().getDrawable(imgs.getResourceId(lastSelected, 0)),
                null,
                requireContext().getDrawable(R.drawable.ic_dropdown),
                null)

            setOnClickListener {
                dialog.show()
            }
        }

        type.observer(viewLifecycleOwner) { num ->
            courseTypeTv.apply {
                //                viewModel.prefs.courseFilter = num
                text = coursesType[num]
                viewModel.courseFilter.postValue(coursesType[num])
                setCompoundDrawablesRelativeWithIntrinsicBounds(requireContext().getDrawable(imgs.getResourceId(num, 0)), null, requireContext().getDrawable(R.drawable.ic_dropdown), null)
            }
        }

        dashboardCoursesRv.setRv(requireContext(), courseListAdapter, true)
        viewModel.isLoggedIn.observer(viewLifecycleOwner) { isLoggedIn ->
            if (isLoggedIn) {
                viewModel.fetchMyCourses()
                viewModel.added.observer(viewLifecycleOwner) {
                    viewModel.courses.observer(viewLifecycleOwner) {
                        courseListAdapter.submitList(it)
                        changeViewState(dashboardCoursesRv, emptyLl, dashboardCourseShimmer, it.isEmpty())
                    }
                }
            } else {
                dashboardMyCourseLoggedOut.isVisible = true
                dashboardMyCourse.isVisible = false
            }
        }

        viewModel.errorLiveData.observer(viewLifecycleOwner) {
            when (it) {
                ErrorStatus.NO_CONNECTION -> {
                    dashboardCourseRoot.showSnackbar(it, Snackbar.LENGTH_SHORT, dashboardBottomNav)
                }
                ErrorStatus.TIMEOUT -> {
                    dashboardCourseRoot.showSnackbar(it, Snackbar.LENGTH_INDEFINITE, dashboardBottomNav) {
                        viewModel.fetchMyCourses()
                    }
                }
                ErrorStatus.UNAUTHORIZED -> {
                    Components.showConfirmation(requireContext(), UNAUTHORIZED) {
                    }
                }
                else -> {
                    dashboardCourseRoot.showSnackbar(it, Snackbar.LENGTH_SHORT, dashboardBottomNav)
                    AppCrashlyticsWrapper.log(it)
                }
            }
            if (courseListAdapter.currentList.isEmpty())
                showEmptyView(emptyView = emptyLl, shimmerView = dashboardCourseShimmer)
        }
        courseListAdapter.onItemClick = itemClickListener
        loginBtn.setOnClickListener { startActivity(intentFor<LoginActivity>()) }
    }

    private fun setUpBottomSheet() {
        val sheetDialog = layoutInflater.inflate(R.layout.bottom_sheet_mycourses, null)
        val list = arrayListOf<SheetItem>()
        repeat(5) {
            list.add(SheetItem(coursesType[it], imgs.getResourceId(it, 0)))
        }
        sheetDialog.run {
            val initialSelectedItem = sharedPrefs.SP_COURSE_FILTER_TYPE
            val sheetAdapter = SheetAdapter(list, initialSelectedItem)
            sheetLv.adapter = sheetAdapter
            sheetLv.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                type.postValue(position)
                sharedPrefs.SP_COURSE_FILTER_TYPE = position
                sheetAdapter.selectedItem = position
                dialog.dismiss()
            }
        }
        dialog.dismissWithAnimation = true
        dialog.setContentView(sheetDialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        courseListAdapter.onItemClick = null
    }
}
