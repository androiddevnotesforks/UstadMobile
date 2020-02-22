package com.ustadmobile.staging.port.android.view

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.toughra.ustadmobile.R
import com.ustadmobile.core.controller.SelectClazzFeaturesPresenter
import com.ustadmobile.core.impl.UMAndroidUtil
import com.ustadmobile.core.view.DismissableDialog
import com.ustadmobile.core.view.SelectClazzFeaturesView
import com.ustadmobile.lib.db.entities.Clazz
import com.ustadmobile.port.android.view.UstadDialogFragment
import io.reactivex.annotations.NonNull


class SelectClazzFeaturesDialogFragment : UstadDialogFragment(), SelectClazzFeaturesView,
        DismissableDialog {
    override val viewContext: Any
        get() = context!!

    internal lateinit var dialog: AlertDialog
    internal lateinit var rootView: View
    private var updatedClazz: Clazz? = null

    private var mPresenter: SelectClazzFeaturesPresenter? = null
    //Context (Activity calling this)
    private var mAttachedContext: Context? = null
    internal lateinit var toolbar: Toolbar
    internal lateinit var attendanceSwitch: Switch
    internal lateinit var activitySwitch: Switch
    internal lateinit var selSwitch: Switch


    //Main Activity should implement this ?
    interface ClazzFeaturesSelectDialogListener {
        fun onSelectClazzesFeaturesResult(clazz: Clazz?)
    }

    @NonNull
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val inflater = context!!.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        rootView = inflater.inflate(R.layout.fragment_select_clazz_features_dialog, null)

        attendanceSwitch = rootView.findViewById(R.id.fragment_select_clazz_features_dialog_attendance_switch)
        activitySwitch = rootView.findViewById(R.id.fragment_select_clazz_features_dialog_activity_switch)
        selSwitch = rootView.findViewById(R.id.fragment_select_clazz_features_dialog_sel_switch)

        attendanceSwitch.setOnCheckedChangeListener { buttonView, isChecked -> mPresenter!!.updateAttendanceFeature(isChecked) }
        activitySwitch.setOnCheckedChangeListener { buttonView, isChecked -> mPresenter!!.updateActivityFeature(isChecked) }
        selSwitch.setOnCheckedChangeListener { buttonView, isChecked -> mPresenter!!.updateSELFeature(isChecked) }

        //Toolbar:
        toolbar = rootView.findViewById(R.id.fragment_select_clazz_features_dialog_toolbar)
        toolbar.setTitle(R.string.features_enabled)

        var upIcon = AppCompatResources.getDrawable(context!!,
                R.drawable.ic_arrow_back_white_24dp)

        upIcon = getTintedDrawable(upIcon, R.color.icons)


        toolbar.navigationIcon = upIcon
        toolbar.setNavigationOnClickListener { v -> finish() }


        mPresenter = SelectClazzFeaturesPresenter(context!!,
                UMAndroidUtil.bundleToMap(arguments), this)
        mPresenter!!.onCreate(UMAndroidUtil.bundleToMap(savedInstanceState))

        //Dialog stuff:
        //Set any view components and its listener (post presenter work)
        dialog = AlertDialog.Builder(context!!,
                R.style.FullScreenDialogStyle)
                .setView(rootView)
                .setTitle("")
                .create()
        return dialog

    }


    fun getTintedDrawable(drawable: Drawable?, color: Int): Drawable {
        var drawable = drawable
        drawable = DrawableCompat.wrap(drawable!!)
        val tintColor = ContextCompat.getColor(context!!, color)
        DrawableCompat.setTint(drawable!!, tintColor)
        return drawable
    }

    override fun updateFeaturesOnView(clazz: Clazz) {
        runOnUiThread (Runnable{
            //Set toggle
            attendanceSwitch.isChecked = clazz.isAttendanceFeature
            activitySwitch.isChecked = clazz.isActivityFeature
            selSwitch.isChecked = clazz.isSelFeature
        })

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        this.mAttachedContext = context
    }

    override fun onDetach() {
        super.onDetach()
        this.mAttachedContext = null
        this.updatedClazz = null
    }

    override fun finish() {
        updatedClazz = mPresenter!!.currentClazz
        if (mAttachedContext is ClazzFeaturesSelectDialogListener) {
            (mAttachedContext as ClazzFeaturesSelectDialogListener).onSelectClazzesFeaturesResult(updatedClazz)
        }
        dialog.dismiss()
    }

    companion object {

        fun newInstance(): SelectClazzFeaturesDialogFragment {
            val fragment = SelectClazzFeaturesDialogFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }

}
