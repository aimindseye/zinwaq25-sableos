package org.sableos.titan2.printtogo.core

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sableos.titan2.printtogo.core.IppPrinter.Operation
import org.sableos.titan2.printtogo.core.IppPrinter.Status

class IppTest {
    private val info =
        PrinterInfo("Sable Print To Go (Q25)", "ipp://192.168.1.5:8631/ipp/print", "urn:uuid:1234")

    private fun request(op: Int, vararg attributes: IppAttribute, id: Int = 7) = IppMessage(
        op,
        id,
        listOf(
            IppGroup(
                Ipp.GROUP_OPERATION,
                listOf(
                    IppAttribute(
                        "attributes-charset",
                        listOf(IppValue.Text("utf-8", Ipp.TAG_CHARSET))
                    ),
                    IppAttribute(
                        "attributes-natural-language",
                        listOf(IppValue.Text("en", Ipp.TAG_LANGUAGE))
                    ),
                    IppAttribute("printer-uri", listOf(IppValue.Text(info.uri, Ipp.TAG_URI)))
                ) + attributes
            )
        )
    )

    private fun text(name: String, value: String, tag: Int = Ipp.TAG_NAME) =
        IppAttribute(name, listOf(IppValue.Text(value, tag)))

    @Test
    fun messagesRoundTripWithCollectionsAndLeaveTheDocumentUnread() {
        val printer = IppPrinter(info) { 5 }
        val response = printer.handle(request(Operation.GET_PRINTER_ATTRIBUTES)) { _, _ -> null }
        val bytes = Ipp.encode(response) + "%PDF-1.7".toByteArray()
        val input = ByteArrayInputStream(bytes)
        val decoded = Ipp.decode(input)
        assertEquals(response, decoded)
        assertEquals("%PDF-1.7", String(input.readBytes()))
        val database = decoded.group(Ipp.GROUP_PRINTER)!!.find("media-col-database")!!.values
        assertEquals(IppPrinter.MEDIA.size, database.size)
        val a4 = database.first() as IppValue.Collection
        val size = a4.members.first {
            it.first == "media-size"
        }.second.single() as IppValue.Collection
        assertEquals(
            listOf(IppValue.Integer(21000)),
            size.members.first {
                it.first == "x-dimension"
            }.second
        )
    }

    @Test
    fun printerAnswersWhatDriverlessClientsAsk() {
        val printer = IppPrinter(info) { 42 }
        val attributes = printer.handle(request(Operation.GET_PRINTER_ATTRIBUTES)) { _, _ -> null }
        assertEquals(Status.OK, attributes.code)
        assertEquals(7, attributes.requestId)
        assertEquals(info.uri, attributes.text(Ipp.GROUP_PRINTER, "printer-uri-supported"))
        assertEquals(42, attributes.int(Ipp.GROUP_PRINTER, "printer-up-time"))
        val formats = attributes.group(
            Ipp.GROUP_PRINTER
        )!!.find("document-format-supported")!!.values
        assertEquals(IppPrinter.FORMATS.toList(), formats.map { (it as IppValue.Text).value })
        val bad =
            request(Operation.VALIDATE_JOB, text("document-format", "image/jpeg", Ipp.TAG_MIME))
        assertEquals(
            Status.DOCUMENT_FORMAT_NOT_SUPPORTED,
            printer.handle(bad) { _, _ ->
                null
            }.code
        )
        assertEquals(
            Status.OPERATION_NOT_SUPPORTED,
            printer.handle(request(0x0010)) { _, _ ->
                null
            }.code
        )
        val old = request(Operation.GET_PRINTER_ATTRIBUTES).copy(version = 0x0300)
        assertEquals(Status.VERSION_NOT_SUPPORTED, printer.handle(old) { _, _ -> null }.code)
    }

    @Test
    fun printJobSavesAndReportsTheJob() {
        val printer = IppPrinter(info) { 1 }
        var seen: Pair<PrintJob, String>? = null
        val done =
            printer.handle(
                request(
                    Operation.PRINT_JOB,
                    text("job-name", "Boarding pass"),
                    text("document-format", IppPrinter.PDF, Ipp.TAG_MIME)
                )
            ) { job, format ->
                seen = job to format
                "Boarding pass 2026-11-05 1331.pdf"
            }
        assertEquals("Boarding pass", seen!!.first.name)
        assertEquals(IppPrinter.PDF, seen!!.second)
        assertEquals(Status.OK, done.code)
        assertEquals(IppPrinter.JobState.COMPLETED, done.int(Ipp.GROUP_JOB, "job-state"))
        assertEquals("${info.uri}/1", done.text(Ipp.GROUP_JOB, "job-uri"))
        assertEquals("anonymous", done.text(Ipp.GROUP_JOB, "job-originating-user-name"))
        assertEquals(1, done.int(Ipp.GROUP_JOB, "time-at-completed"))
        val failed = printer.handle(request(Operation.PRINT_JOB)) { _, _ -> null }
        assertEquals(Status.INTERNAL_ERROR, failed.code)
        assertEquals(IppPrinter.JobState.ABORTED, failed.int(Ipp.GROUP_JOB, "job-state"))
        val pending = printer.handle(request(Operation.GET_JOBS)) { _, _ -> null }
        assertEquals(0, pending.groups.count { it.tag == Ipp.GROUP_JOB })
        val ended = request(Operation.GET_JOBS, text("which-jobs", "completed", Ipp.TAG_KEYWORD))
        val jobs = printer.handle(ended) { _, _ -> null }
        assertEquals(2, jobs.groups.count { it.tag == Ipp.GROUP_JOB })
        assertEquals("Boarding pass 2026-11-05 1331.pdf", printer.received.first().savedAs)
    }

    @Test
    fun createJobThenSendDocument() {
        val printer = IppPrinter(info) { 1 }
        val created = printer.handle(request(Operation.CREATE_JOB, text("job-name", "Report"))) {
                _,
                _
            ->
            null
        }
        val id = created.int(Ipp.GROUP_JOB, "job-id")!!
        val send =
            request(
                Operation.SEND_DOCUMENT,
                IppAttribute("job-id", listOf(IppValue.Integer(id))),
                text("document-format", IppPrinter.PWG_RASTER, Ipp.TAG_MIME),
                IppAttribute("last-document", listOf(IppValue.Bool(true)))
            )
        val noLast =
            request(Operation.SEND_DOCUMENT, IppAttribute("job-id", listOf(IppValue.Integer(id))))
        assertEquals(Status.BAD_REQUEST, printer.handle(noLast) { _, _ -> "x" }.code)
        var format: String? = null
        val sent = printer.handle(send) { _, f -> "Report.pdf".also { format = f } }
        assertEquals(Status.OK, sent.code)
        assertEquals(IppPrinter.PWG_RASTER, format)
        val missing =
            request(
                Operation.SEND_DOCUMENT,
                IppAttribute("job-id", listOf(IppValue.Integer(99))),
                IppAttribute("last-document", listOf(IppValue.Bool(true)))
            )
        assertEquals(Status.NOT_FOUND, printer.handle(missing) { _, _ -> "x" }.code)
        assertNull(printer.received.firstOrNull { it.id == 99 })
    }

    @Test
    fun malformedRequestsAreRefused() {
        val printer = IppPrinter(info) { 1 }
        val noId = request(Operation.GET_PRINTER_ATTRIBUTES, id = 0)
        assertEquals(Status.BAD_REQUEST, printer.handle(noId) { _, _ -> null }.code)
        val good = request(Operation.GET_PRINTER_ATTRIBUTES)
        val noTarget = good.copy(
            groups = listOf(
                good.groups[0].copy(attributes = good.groups[0].attributes.take(2))
            )
        )
        assertEquals(Status.BAD_REQUEST, printer.handle(noTarget) { _, _ -> null }.code)
        val noGroups = good.copy(groups = emptyList())
        assertEquals(Status.BAD_REQUEST, printer.handle(noGroups) { _, _ -> null }.code)
    }
}
