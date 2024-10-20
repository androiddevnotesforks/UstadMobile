package com.ustadmobile.core.util.graph

class TimeFormatter() : LabelValueFormatter {

    override fun format(option: Any): String {
        return (option as Float / (1000*60*60)).toString()
    }

    override fun formatAsList(option: List<Any>): List<String> {
        return option.map { format(it) }
    }


}