package org.sableos.phone.android

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import org.sableos.phone.core.CallFormat
import org.sableos.phone.core.CallHistory
import org.sableos.phone.core.PhoneSearch
import org.sableos.phone.core.PhoneText.t

/** The rows of the Calls and Contacts lists: day or group headers, call rows and contact rows. */
class PhoneListAdapter(private val context: Context, private val ui: Ui) : BaseAdapter() {
    sealed interface Item {
        data class Header(val title: String) : Item

        data class CallRow(val row: CallHistory.Row, val hideNames: Boolean) : Item

        data class PersonRow(val person: PhoneSearch.Person) : Item
    }

    var items: List<Item> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Item = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int = if (items[position] is Item.Header) 0 else 1

    override fun isEnabled(position: Int): Boolean = items[position] !is Item.Header

    override fun areAllItemsEnabled(): Boolean = false

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = items[position]
        if (item is Item.Header) {
            return (convertView as? TextView ?: header()).apply {
                text =
                    item.title
            }
        }
        val row = convertView as? LinearLayout ?: row()
        return when (item) {
            is Item.CallRow -> fill(row, callTitle(item), callLine(item), item.row.missed)
            is Item.PersonRow -> fill(row, item.person.name, item.person.numbers.first(), false)
            is Item.Header -> row
        }
    }

    private fun header(): TextView = ui.text(Ui.SMALL_SP, ui.palette.accent, bold = true).apply {
        setPadding(ui.dp(Ui.PAD), ui.dp(HEADER_TOP), ui.dp(Ui.PAD), ui.dp(HEADER_BOTTOM))
    }

    private fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(Ui.PAD), ui.dp(ROW_PAD), ui.dp(Ui.PAD), ui.dp(ROW_PAD))
        addView(ui.text(Ui.BODY_SP, bold = true).apply { maxLines = 1 })
        addView(ui.text(Ui.SMALL_SP, ui.palette.muted).apply { maxLines = 1 })
    }

    private fun fill(row: LinearLayout, title: String, line: String, missed: Boolean): View =
        row.apply {
            (getChildAt(0) as TextView).apply {
                text = title
                setTextColor(if (missed) ui.palette.danger else ui.palette.text)
            }
            (getChildAt(1) as TextView).text = line
        }

    private fun callTitle(item: Item.CallRow): String {
        val call = item.row.latest
        val who = when {
            item.hideNames -> t("contact_hidden")
            call.hidden || call.number.isEmpty() -> t("private_number")
            else -> call.name ?: CallInfo.formatted(context, call.number)
        }
        return if (item.row.count > 1) t("call_count", who, item.row.count) else who
    }

    private fun callLine(item: Item.CallRow): String {
        val call = item.row.latest
        val kind = t(KIND_KEYS.getValue(call.kind))
        val time = DateFormat.getTimeFormat(context).format(call.timeMillis)
        val showNumber = !item.hideNames && call.name != null && !call.hidden
        val number = if (showNumber) CallInfo.formatted(context, call.number) else ""
        val length = if (call.durationSeconds > 0) CallFormat.duration(call.durationSeconds) else ""
        return listOf(kind, number, time, length).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    companion object {
        private const val HEADER_TOP = 10
        private const val HEADER_BOTTOM = 4
        private const val ROW_PAD = 8

        private val KIND_KEYS = mapOf(
            CallHistory.Kind.INCOMING to "kind_incoming",
            CallHistory.Kind.OUTGOING to "kind_outgoing",
            CallHistory.Kind.MISSED to "kind_missed",
            CallHistory.Kind.DECLINED to "kind_declined",
            CallHistory.Kind.BLOCKED to "kind_blocked",
            CallHistory.Kind.VOICEMAIL to "kind_voicemail"
        )

        /** "Today", "Yesterday" or the date, for a day section. */
        fun dayTitle(context: Context, section: CallHistory.Section, millis: Long): String =
            when (section.label) {
                CallHistory.DayLabel.TODAY -> t("today")

                CallHistory.DayLabel.YESTERDAY -> t("yesterday")

                CallHistory.DayLabel.DATE -> DateUtils.formatDateTime(
                    context,
                    millis,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
                        DateUtils.FORMAT_ABBREV_MONTH
                )
            }
    }
}
