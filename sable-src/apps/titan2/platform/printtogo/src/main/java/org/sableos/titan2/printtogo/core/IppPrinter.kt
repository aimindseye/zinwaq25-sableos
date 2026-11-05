package org.sableos.titan2.printtogo.core

import org.sableos.titan2.printtogo.core.Ipp.GROUP_JOB
import org.sableos.titan2.printtogo.core.Ipp.GROUP_OPERATION
import org.sableos.titan2.printtogo.core.Ipp.GROUP_PRINTER
import org.sableos.titan2.printtogo.core.Ipp.TAG_CHARSET
import org.sableos.titan2.printtogo.core.Ipp.TAG_ENUM
import org.sableos.titan2.printtogo.core.Ipp.TAG_KEYWORD
import org.sableos.titan2.printtogo.core.Ipp.TAG_LANGUAGE
import org.sableos.titan2.printtogo.core.Ipp.TAG_MIME
import org.sableos.titan2.printtogo.core.Ipp.TAG_NAME
import org.sableos.titan2.printtogo.core.Ipp.TAG_NO_VALUE
import org.sableos.titan2.printtogo.core.Ipp.TAG_TEXT
import org.sableos.titan2.printtogo.core.Ipp.TAG_URI

/** The printer as computers see it on the Wi-Fi. */
data class PrinterInfo(
    val name: String,
    /** ipp://address:port/ipp/print */
    val uri: String,
    /** urn:uuid:... */
    val uuid: String
)

data class PrintJob(
    val id: Int,
    val name: String,
    val format: String,
    val state: Int,
    /** The file name it was saved as, once saved. */
    val savedAs: String? = null,
    /** requesting-user-name, as the computer sent it. */
    val user: String = "anonymous",
    /** printer-up-time when it was made, and when it ended (0 while it runs). */
    val createdAt: Int = 0,
    val endedAt: Int = 0
)

/**
 * A print-to-file printer: the IPP Everywhere subset that macOS, Windows 10/11 and Linux (CUPS) use to print
 * without a driver. It never prints on paper: each document goes to [IppPrinter.handle]'s `receive`, which saves
 * it on the phone. Jobs are kept in memory only, the last [KEEP_JOBS].
 */
class IppPrinter(private val info: PrinterInfo, private val upTimeSeconds: () -> Int) {
    private val jobs = linkedMapOf<Int, PrintJob>()
    private var nextId = 1

    /** Jobs received since the printer started, oldest first. */
    val received: List<PrintJob> get() = jobs.values.toList()

    /**
     * Answers [request]. For Print-Job and Send-Document, `receive(job, format)` reads the document data that
     * follows the request and returns the name it was saved as, or null when it could not be saved.
     */
    fun handle(request: IppMessage, receive: (PrintJob, String) -> String?): IppMessage {
        val refused = refusal(request)
        return if (refused != null) reply(request, refused) else dispatch(request, receive)
    }

    /** A request this printer cannot even read: an IPP version it does not speak, or a malformed one. */
    private fun refusal(request: IppMessage): Int? = when {
        request.version shr MAJOR_SHIFT !in SUPPORTED_MAJOR -> Status.VERSION_NOT_SUPPORTED
        request.requestId <= 0 || !wellFormed(request) -> Status.BAD_REQUEST
        else -> null
    }

    private fun dispatch(request: IppMessage, receive: (PrintJob, String) -> String?): IppMessage =
        when (request.code) {
            Operation.GET_PRINTER_ATTRIBUTES ->
                reply(
                    request,
                    Status.OK,
                    IppGroup(GROUP_PRINTER, requested(request, printerAttributes()))
                )

            Operation.VALIDATE_JOB ->
                if (format(request) != null) reply(request, Status.OK) else badFormat(request)

            Operation.PRINT_JOB -> printJob(request, receive)

            Operation.CREATE_JOB -> reply(
                request,
                Status.OK,
                jobGroup(create(request, DEFAULT_FORMAT))
            )

            Operation.SEND_DOCUMENT -> sendDocument(request, receive)

            Operation.CANCEL_JOB, Operation.CLOSE_JOB -> cancelOrClose(request)

            Operation.GET_JOB_ATTRIBUTES -> {
                val job = job(request)
                if (job !=
                    null
                ) {
                    reply(request, Status.OK, jobGroup(job))
                } else {
                    reply(request, Status.NOT_FOUND)
                }
            }

            Operation.GET_JOBS -> replyAll(request, Status.OK, listJobs(request))

            Operation.IDENTIFY_PRINTER -> reply(request, Status.OK)

            else -> reply(request, Status.OPERATION_NOT_SUPPORTED)
        }

    private fun printJob(request: IppMessage, receive: (PrintJob, String) -> String?): IppMessage {
        val format = format(request)
        return if (format == null) {
            badFormat(request)
        } else {
            finish(request, receive(create(request, format), format), create = true)
        }
    }

    private fun sendDocument(
        request: IppMessage,
        receive: (PrintJob, String) -> String?
    ): IppMessage {
        val job = job(request)
        val format = format(request)
        return when {
            job == null -> reply(request, Status.NOT_FOUND)

            // RFC 8011 4.3.1: last-document is required. Each job here holds one document.
            request.group(
                GROUP_OPERATION
            )?.find("last-document") == null -> reply(request, Status.BAD_REQUEST)

            format == null -> badFormat(request)

            else -> {
                val updated = job.copy(format = format)
                jobs[job.id] = updated
                finish(request, receive(updated, format), create = false, job = updated)
            }
        }
    }

    private fun cancelOrClose(request: IppMessage): IppMessage {
        val job = job(request)
        return when {
            job == null -> reply(request, Status.NOT_FOUND)

            request.code == Operation.CLOSE_JOB -> reply(request, Status.OK)

            // A finished job cannot be cancelled (RFC 8011 4.3.3).
            job.state >= JobState.CANCELED -> reply(request, Status.NOT_POSSIBLE)

            else -> {
                jobs[job.id] = job.copy(state = JobState.CANCELED)
                reply(request, Status.OK)
            }
        }
    }

    /**
     * RFC 8011 4.2.6: the jobs which-jobs names (not-completed unless asked), newest first, with job-id and
     * job-uri unless requested-attributes asks for more.
     */
    private fun listJobs(request: IppMessage): List<IppGroup> {
        val completed = request.text(GROUP_OPERATION, "which-jobs") == "completed"
        val asked =
            request.group(GROUP_OPERATION)?.find("requested-attributes")?.values
                ?.mapNotNull { (it as? IppValue.Text)?.value }
                ?.toSet()
                ?: setOf("job-id", "job-uri")
        return jobs.values
            .filter { (it.state >= JobState.CANCELED) == completed }
            .reversed()
            .map { job ->
                val group = jobGroup(job)
                if ("all" in asked || "job-description" in asked) {
                    group
                } else {
                    group.copy(attributes = group.attributes.filter { it.name in asked })
                }
            }
    }

    /**
     * RFC 8011 4.2.5: only the attributes asked for in requested-attributes; "all", a group name or no list
     * gives every one (this printer's attributes are all printer-description or job-template ones).
     */
    private fun requested(request: IppMessage, all: List<IppAttribute>): List<IppAttribute> {
        val asked =
            request.group(GROUP_OPERATION)?.find("requested-attributes")?.values
                ?.mapNotNull { (it as? IppValue.Text)?.value }
                ?.toSet()
                .orEmpty()
        if (asked.isEmpty() || asked.any { it in GROUP_NAMES }) return all
        return all.filter { it.name in asked }
    }

    /**
     * RFC 8011 4.1.4 and 4.2: the operation group starts with the charset, then the natural language, and
     * names its target (printer-uri, or job-uri for a job).
     */
    private fun wellFormed(request: IppMessage): Boolean {
        val first = request.groups.firstOrNull() ?: return false
        val names = first.attributes.take(2).map { it.name }
        val target = first.find("printer-uri") ?: first.find("job-uri")
        return first.tag == GROUP_OPERATION &&
            names == listOf("attributes-charset", "attributes-natural-language") &&
            target != null
    }

    private fun finish(
        request: IppMessage,
        saved: String?,
        create: Boolean,
        job: PrintJob? = null
    ): IppMessage {
        val current = job ?: jobs.values.last()
        val state = if (saved != null) JobState.COMPLETED else JobState.ABORTED
        val done = current.copy(state = state, savedAs = saved, endedAt = upTimeSeconds())
        jobs[done.id] = done
        val status = if (saved != null) Status.OK else Status.INTERNAL_ERROR
        return if (create ||
            saved != null
        ) {
            reply(request, status, jobGroup(done))
        } else {
            reply(request, status)
        }
    }

    private fun create(request: IppMessage, format: String): PrintJob {
        val name =
            request.text(GROUP_OPERATION, "job-name")
                ?: request.text(GROUP_OPERATION, "document-name")
                ?: request.text(GROUP_JOB, "job-name")
                ?: "Print"
        val user = request.text(GROUP_OPERATION, "requesting-user-name") ?: "anonymous"
        val job =
            PrintJob(
                nextId++,
                name,
                format,
                JobState.PROCESSING,
                user = user,
                createdAt = upTimeSeconds()
            )
        jobs[job.id] = job
        while (jobs.size > KEEP_JOBS) jobs.remove(jobs.keys.first())
        return job
    }

    private fun job(request: IppMessage): PrintJob? =
        request.int(GROUP_OPERATION, "job-id")?.let { jobs[it] }
            ?: request.text(
                GROUP_OPERATION,
                "job-uri"
            )?.substringAfterLast('/')?.toIntOrNull()?.let {
                jobs[it]
            }

    /** The request's document format if this printer takes it; no format means PDF. */
    private fun format(request: IppMessage): String? {
        val format = request.text(GROUP_OPERATION, "document-format") ?: return DEFAULT_FORMAT
        return format.takeIf { it in FORMATS }
    }

    private fun badFormat(request: IppMessage) =
        reply(request, Status.DOCUMENT_FORMAT_NOT_SUPPORTED)

    private fun reply(request: IppMessage, status: Int, vararg groups: IppGroup): IppMessage =
        replyAll(request, status, groups.toList())

    private fun replyAll(request: IppMessage, status: Int, groups: List<IppGroup>): IppMessage {
        val operation =
            IppGroup(
                GROUP_OPERATION,
                listOf(
                    text("attributes-charset", "utf-8", TAG_CHARSET),
                    text("attributes-natural-language", "en", TAG_LANGUAGE)
                )
            )
        return IppMessage(status, request.requestId, listOf(operation) + groups, request.version)
    }

    private fun jobGroup(job: PrintJob) = IppGroup(
        GROUP_JOB,
        listOf(
            int("job-id", job.id),
            text("job-uri", "${info.uri}/${job.id}", TAG_URI),
            text("job-printer-uri", info.uri, TAG_URI),
            text("job-name", job.name, TAG_NAME),
            int("job-state", job.state, TAG_ENUM),
            text("job-state-reasons", reasonFor(job.state), TAG_KEYWORD),
            text("job-originating-user-name", job.user, TAG_NAME),
            int("time-at-creation", job.createdAt),
            int("time-at-processing", job.createdAt),
            ended(job),
            int("job-printer-up-time", upTimeSeconds())
        )
    )

    /** RFC 8011 5.3.14.7: time-at-completed is no-value until the job ends. */
    private fun ended(job: PrintJob) = if (job.endedAt > 0) {
        int("time-at-completed", job.endedAt)
    } else {
        IppAttribute("time-at-completed", listOf(IppValue.Raw(TAG_NO_VALUE, ByteArray(0))))
    }

    private fun reasonFor(state: Int) = when (state) {
        JobState.COMPLETED -> "job-completed-successfully"
        JobState.ABORTED -> "aborted-by-system"
        JobState.CANCELED -> "job-canceled-by-user"
        else -> "none"
    }

    private fun printerAttributes(): List<IppAttribute> = identity() + paper() + options()

    /** Who the printer is, what it speaks and takes. */
    private fun identity(): List<IppAttribute> = listOf(
        text("printer-uri-supported", info.uri, TAG_URI),
        text("uri-security-supported", "none", TAG_KEYWORD),
        text("uri-authentication-supported", "none", TAG_KEYWORD),
        text("printer-name", info.name, TAG_NAME),
        text("printer-info", info.name, TAG_TEXT),
        text("printer-location", "", TAG_TEXT),
        text(
            "printer-more-info",
            info.uri.replace("ipp://", "http://").substringBefore("/ipp"),
            TAG_URI
        ),
        text("printer-make-and-model", MAKE_AND_MODEL, TAG_TEXT),
        text("printer-device-id", DEVICE_ID, TAG_TEXT),
        text("printer-uuid", info.uuid, TAG_URI),
        int("printer-state", PRINTER_IDLE, TAG_ENUM),
        text("printer-state-reasons", "none", TAG_KEYWORD),
        bool("printer-is-accepting-jobs", true),
        int("printer-up-time", upTimeSeconds()),
        int("queued-job-count", 0),
        int("pages-per-minute", PAGES_PER_MINUTE),
        int("pages-per-minute-color", PAGES_PER_MINUTE),
        texts("ipp-versions-supported", listOf("1.1", "2.0"), TAG_KEYWORD),
        IppAttribute(
            "operations-supported",
            Operation.ALL.map {
                IppValue.Integer(it, TAG_ENUM)
            }
        ),
        text("charset-configured", "utf-8", TAG_CHARSET),
        text("charset-supported", "utf-8", TAG_CHARSET),
        text("natural-language-configured", "en", TAG_LANGUAGE),
        text("generated-natural-language-supported", "en", TAG_LANGUAGE),
        text("document-format-default", DEFAULT_FORMAT, TAG_MIME),
        texts("document-format-supported", FORMATS.toList(), TAG_MIME),
        text("pdl-override-supported", "attempted", TAG_KEYWORD),
        text("compression-supported", "none", TAG_KEYWORD),
        bool("color-supported", true),
        texts("print-color-mode-supported", listOf("color", "monochrome"), TAG_KEYWORD),
        text("print-color-mode-default", "color", TAG_KEYWORD),
        text("sides-supported", "one-sided", TAG_KEYWORD),
        text("sides-default", "one-sided", TAG_KEYWORD),
        IppAttribute("copies-supported", listOf(IppValue.Range(1, 1))),
        int("copies-default", 1)
    )

    /** Paper sizes and margins. */
    private fun paper(): List<IppAttribute> = listOf(
        texts("media-supported", MEDIA.map { it.keyword }, TAG_KEYWORD),
        text("media-default", MEDIA.first().keyword, TAG_KEYWORD),
        texts("media-ready", MEDIA.map { it.keyword }, TAG_KEYWORD),
        IppAttribute("media-col-default", listOf(MEDIA.first().collection())),
        IppAttribute("media-size-supported", MEDIA.map { it.size() }),
        IppAttribute("media-col-database", MEDIA.map { it.collection() }),
        IppAttribute("media-col-ready", MEDIA.map { it.collection() }),
        texts(
            "media-col-supported",
            listOf(
                "media-size",
                "media-top-margin",
                "media-bottom-margin",
                "media-left-margin",
                "media-right-margin"
            ),
            TAG_KEYWORD
        ),
        int("media-top-margin-supported", 0),
        int("media-bottom-margin-supported", 0),
        int("media-left-margin-supported", 0),
        int("media-right-margin-supported", 0),
        text("media-source-supported", "auto", TAG_KEYWORD),
        text("media-type-supported", "stationery", TAG_KEYWORD)
    )

    /** Resolution, quality and the rest of what a print dialog offers. */
    private fun options(): List<IppAttribute> = listOf(
        text("output-bin-supported", "face-up", TAG_KEYWORD),
        text("output-bin-default", "face-up", TAG_KEYWORD),
        IppAttribute(
            "orientation-requested-supported",
            (PORTRAIT..LANDSCAPE).map {
                IppValue.Integer(it, TAG_ENUM)
            }
        ),
        int("orientation-requested-default", PORTRAIT, TAG_ENUM),
        IppAttribute(
            "print-quality-supported",
            listOf(IppValue.Integer(QUALITY_NORMAL, TAG_ENUM))
        ),
        int("print-quality-default", QUALITY_NORMAL, TAG_ENUM),
        IppAttribute("printer-resolution-supported", listOf(IppValue.Resolution(DPI, DPI))),
        IppAttribute("printer-resolution-default", listOf(IppValue.Resolution(DPI, DPI))),
        IppAttribute(
            "pwg-raster-document-resolution-supported",
            listOf(IppValue.Resolution(DPI, DPI))
        ),
        texts("pwg-raster-document-type-supported", listOf("sgray_8", "srgb_8"), TAG_KEYWORD),
        text("pwg-raster-document-sheet-back", "normal", TAG_KEYWORD),
        texts("urf-supported", URF, TAG_KEYWORD),
        int("finishings-supported", FINISHINGS_NONE, TAG_ENUM),
        int("finishings-default", FINISHINGS_NONE, TAG_ENUM),
        text("job-hold-until-supported", "no-hold", TAG_KEYWORD),
        text("job-hold-until-default", "no-hold", TAG_KEYWORD),
        text("job-sheets-supported", "none", TAG_NAME),
        text("job-sheets-default", "none", TAG_NAME),
        texts("which-jobs-supported", listOf("completed", "not-completed"), TAG_KEYWORD),
        text("identify-actions-supported", "display", TAG_KEYWORD),
        text("printer-kind", "document", TAG_KEYWORD),
        text(
            "multiple-document-handling-supported",
            "separate-documents-uncollated-copies",
            TAG_KEYWORD
        ),
        bool("page-ranges-supported", false)
    )

    /** A paper size, in hundredths of a millimetre. */
    data class Media(val keyword: String, val width: Int, val height: Int) {
        fun size(): IppValue.Collection = IppValue.Collection(
            listOf(
                "x-dimension" to listOf(IppValue.Integer(width)),
                "y-dimension" to listOf(IppValue.Integer(height))
            )
        )

        fun collection(): IppValue.Collection = IppValue.Collection(
            listOf(
                "media-size" to listOf(size()),
                "media-top-margin" to listOf(IppValue.Integer(0)),
                "media-bottom-margin" to listOf(IppValue.Integer(0)),
                "media-left-margin" to listOf(IppValue.Integer(0)),
                "media-right-margin" to listOf(IppValue.Integer(0))
            )
        )
    }

    object Operation {
        const val PRINT_JOB = 0x0002
        const val VALIDATE_JOB = 0x0004
        const val CREATE_JOB = 0x0005
        const val SEND_DOCUMENT = 0x0006
        const val CANCEL_JOB = 0x0008
        const val GET_JOB_ATTRIBUTES = 0x0009
        const val GET_JOBS = 0x000A
        const val GET_PRINTER_ATTRIBUTES = 0x000B
        const val CLOSE_JOB = 0x003B
        const val IDENTIFY_PRINTER = 0x003C
        val ALL =
            listOf(
                PRINT_JOB, VALIDATE_JOB, CREATE_JOB, SEND_DOCUMENT, CANCEL_JOB,
                GET_JOB_ATTRIBUTES, GET_JOBS, GET_PRINTER_ATTRIBUTES, CLOSE_JOB, IDENTIFY_PRINTER
            )
    }

    object Status {
        const val OK = 0x0000
        const val BAD_REQUEST = 0x0400
        const val NOT_POSSIBLE = 0x0404
        const val NOT_FOUND = 0x0406
        const val DOCUMENT_FORMAT_NOT_SUPPORTED = 0x040A
        const val INTERNAL_ERROR = 0x0500
        const val OPERATION_NOT_SUPPORTED = 0x0501
        const val VERSION_NOT_SUPPORTED = 0x0503
    }

    object JobState {
        const val PROCESSING = 5
        const val CANCELED = 7
        const val ABORTED = 8
        const val COMPLETED = 9
    }

    companion object {
        const val PDF = "application/pdf"
        const val PWG_RASTER = "image/pwg-raster"
        const val URF_RASTER = "image/urf"
        const val OCTET_STREAM = "application/octet-stream"
        const val DEFAULT_FORMAT = PDF
        val FORMATS = linkedSetOf(PDF, PWG_RASTER, URF_RASTER, OCTET_STREAM)

        /** Raster pages come at this resolution; PDF keeps its own. */
        const val DPI = 300
        const val KEEP_JOBS = 20

        /** IPP/1.x and 2.x; the minor version is not checked. */
        private val SUPPORTED_MAJOR = 1..2
        private const val MAJOR_SHIFT = 8

        private val GROUP_NAMES =
            setOf("all", "printer-description", "job-template", "media-col-database")

        const val MAKE_AND_MODEL = "SableOS Print To Go"
        const val DEVICE_ID = "MFG:SableOS;MDL:Print To Go;CMD:PDF,PWGRaster,URF;CLS:PRINTER;"
        val URF = listOf("V1.4", "CP1", "PQ4", "RS300", "SRGB24", "W8")

        val MEDIA =
            listOf(
                Media("iso_a4_210x297mm", 21000, 29700),
                Media("na_letter_8.5x11in", 21590, 27940),
                Media("na_legal_8.5x14in", 21590, 35560)
            )

        private const val PRINTER_IDLE = 3

        /** Saving a page takes well under a second. */
        private const val PAGES_PER_MINUTE = 60
        private const val PORTRAIT = 3
        private const val LANDSCAPE = 4
        private const val QUALITY_NORMAL = 4
        private const val FINISHINGS_NONE = 3

        private fun text(name: String, value: String, tag: Int) =
            IppAttribute(name, listOf(IppValue.Text(value, tag)))

        private fun texts(name: String, values: List<String>, tag: Int) =
            IppAttribute(name, values.map { IppValue.Text(it, tag) })

        private fun int(name: String, value: Int, tag: Int = Ipp.TAG_INTEGER) =
            IppAttribute(name, listOf(IppValue.Integer(value, tag)))

        private fun bool(name: String, value: Boolean) =
            IppAttribute(name, listOf(IppValue.Bool(value)))
    }
}
