package org.sableos.phone.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.sableos.phone.core.Avatar
import org.sableos.phone.core.CallFormat
import org.sableos.phone.core.CallHistory
import org.sableos.phone.core.PhoneSearch
import org.sableos.phone.core.PhoneText.t

/** The rows of the Calls, Favorites and Contacts lists: day or group headers, call rows and contact rows. */
class PhoneListAdapter(private val context: Context, private val ui: Ui) : BaseAdapter() {
    private val photos = ContactPhotos(context)

    sealed interface Item {
        data class Header(val title: String) : Item

        data class CallRow(val row: CallHistory.Row, val hideNames: Boolean) : Item

        /** A contact; [line] replaces the number under the name (Favorites adds the speed dial letter). */
        data class PersonRow(val person: PhoneSearch.Person, val line: String? = null) : Item
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
            is Item.CallRow -> fill(
                row,
                Line(callTitle(item), callLine(item), item.row.missed),
                callAvatar(item)
            )

            is Item.PersonRow -> fill(
                row,
                Line(item.person.name, item.line ?: item.person.numbers.first(), false),
                Face(photos.forContact(item.person.id), Avatar.initials(item.person.name))
            )

            is Item.Header -> row
        }
    }

    private fun header(): TextView = ui.text(Ui.SMALL_SP, ui.palette.accent, bold = true).apply {
        setPadding(ui.dp(Ui.PAD), ui.dp(HEADER_TOP), ui.dp(Ui.PAD), ui.dp(HEADER_BOTTOM))
    }

    /** A row's two lines; a missed call's title is red. */
    private data class Line(val title: String, val line: String, val missed: Boolean)

    /** The round avatar: the contact's photo, else its initials on a raised disc. */
    private data class Face(val photo: Bitmap?, val initials: String)

    /** Avatar, then the title and line, as BlackBerry 10's Phone lists them. */
    private fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(Ui.PAD), ui.dp(ROW_PAD), ui.dp(Ui.PAD), ui.dp(ROW_PAD))
        val size = ui.dp(AVATAR_DP)
        addView(
            FrameLayout(context).apply {
                addView(
                    ui.text(Ui.SMALL_SP, ui.palette.text, bold = true).apply {
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(ui.palette.surface)
                        }
                    },
                    FrameLayout.LayoutParams(size, size)
                )
                addView(
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        clipToOutline = true
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
                    },
                    FrameLayout.LayoutParams(size, size)
                )
            },
            LinearLayout.LayoutParams(size, size).apply { marginEnd = ui.dp(Ui.PAD) }
        )
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(ui.text(Ui.BODY_SP, bold = true).apply { maxLines = 1 })
                addView(ui.text(Ui.SMALL_SP, ui.palette.muted).apply { maxLines = 1 })
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun fill(row: LinearLayout, text: Line, face: Face): View = row.apply {
        val avatar = getChildAt(0) as FrameLayout
        (avatar.getChildAt(0) as TextView).text = face.initials
        (avatar.getChildAt(1) as ImageView).apply {
            setImageBitmap(face.photo)
            visibility = if (face.photo == null) View.GONE else View.VISIBLE
        }
        val lines = getChildAt(1) as LinearLayout
        (lines.getChildAt(0) as TextView).apply {
            this.text = text.title
            setTextColor(if (text.missed) ui.palette.danger else ui.palette.text)
        }
        (lines.getChildAt(1) as TextView).text = text.line
    }

    /** A hidden or private caller shows no photo and no initials of a name the list does not show. */
    private fun callAvatar(item: Item.CallRow): Face {
        val call = item.row.latest
        val name = call.name?.takeUnless { item.hideNames || call.hidden || call.number.isEmpty() }
        return name?.let { Face(photos.forNumber(call.number), Avatar.initials(it)) }
            ?: Face(null, "#")
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
        private const val AVATAR_DP = 40

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
