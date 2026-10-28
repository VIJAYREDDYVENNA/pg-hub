package com.hostelmanagement.payments;

import com.hostelmanagement.payments.dto.FinanceDtos.PaymentAllocationDto;
import com.hostelmanagement.payments.dto.FinanceDtos.ReceiptDto;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/** Renders a printable A5 payment receipt. */
@Service
public class ReceiptPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color ORANGE = new Color(0xFF, 0x5A, 0x0A);

    public byte[] render(ReceiptDto receipt) {
        var p = receipt.payment();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, ORANGE);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        Paragraph head = new Paragraph(p.hostelName(), title);
        doc.add(head);
        if (receipt.hostelAddress() != null) {
            doc.add(new Paragraph(receipt.hostelAddress(), small));
        }
        if (receipt.hostelContact() != null) {
            doc.add(new Paragraph(receipt.hostelContact(), small));
        }
        Paragraph rt = new Paragraph("PAYMENT RECEIPT" + ("REVERSED".equals(p.status()) ? " (REVERSED)" : ""), bold);
        rt.setSpacingBefore(12);
        rt.setSpacingAfter(8);
        doc.add(rt);

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        row(info, "Receipt no.", p.receiptNumber(), bold, normal);
        row(info, "Date", DATE.format(p.paymentDate()), bold, normal);
        row(info, "Received from", p.residentName(), bold, normal);
        if (p.roomNumber() != null) {
            row(info, "Room / Bed", p.roomNumber() + " / " + p.bedLabel(), bold, normal);
        }
        row(info, "Payment method", p.method().replace('_', ' '), bold, normal);
        if (p.transactionReference() != null) {
            row(info, "Reference", p.transactionReference(), bold, normal);
        }
        row(info, "Amount received", "Rs. " + p.amount().toPlainString(), bold, bold);
        doc.add(info);

        if (!p.allocations().isEmpty()) {
            Paragraph ap = new Paragraph("Applied to", bold);
            ap.setSpacingBefore(10);
            ap.setSpacingAfter(4);
            doc.add(ap);
            PdfPTable t = new PdfPTable(new float[]{3, 4, 2});
            t.setWidthPercentage(100);
            header(t, "Invoice", bold);
            header(t, "Period / type", bold);
            header(t, "Amount", bold);
            for (PaymentAllocationDto a : p.allocations()) {
                t.addCell(cell(a.invoiceNumber(), normal));
                String period = a.periodStart() == null ? a.invoiceType()
                        : DATE.format(a.periodStart()) + " - " + DATE.format(a.periodEnd());
                t.addCell(cell(period, normal));
                PdfPCell amt = cell(a.amount().toPlainString(), normal);
                amt.setHorizontalAlignment(Element.ALIGN_RIGHT);
                t.addCell(amt);
            }
            doc.add(t);
        }
        if (p.unallocatedAmount().signum() > 0) {
            doc.add(new Paragraph("Advance / credit retained: Rs. " + p.unallocatedAmount().toPlainString(), normal));
        }
        Paragraph foot = new Paragraph("Recorded by " + p.recordedByName()
                + ". This is a computer-generated receipt.", small);
        foot.setSpacingBefore(16);
        doc.add(foot);
        doc.close();
        return out.toByteArray();
    }

    private static void row(PdfPTable t, String k, String v, Font kf, Font vf) {
        PdfPCell a = new PdfPCell(new Phrase(k, kf));
        PdfPCell b = new PdfPCell(new Phrase(v, vf));
        a.setBorder(0);
        b.setBorder(0);
        a.setPaddingBottom(4);
        b.setPaddingBottom(4);
        t.addCell(a);
        t.addCell(b);
    }

    private static void header(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(new Color(0xFF, 0xF0, 0xE5));
        t.addCell(c);
    }

    private static PdfPCell cell(String text, Font f) {
        return new PdfPCell(new Phrase(text == null ? "" : text, f));
    }
}
