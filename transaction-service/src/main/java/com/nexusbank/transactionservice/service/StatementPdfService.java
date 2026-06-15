package com.nexusbank.transactionservice.service;

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
import com.nexusbank.transactionservice.dto.response.StatementResponse;
import com.nexusbank.transactionservice.dto.response.TransactionResponse;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Renders an account statement ({@link StatementResponse}) into a real,
 * server-generated PDF document using OpenPDF (F16).
 */
@Service
public class StatementPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private static final Color BRAND = new Color(37, 99, 235);
    private static final Color HEADER_BG = new Color(15, 23, 42);
    private static final Color ROW_ALT = new Color(241, 245, 249);

    public byte[] generate(StatementResponse statement) {
        Document document = new Document(PageSize.A4, 36, 36, 48, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, statement);
            addSummary(document, statement);
            addTransactions(document, statement);
            addFooter(document);

            document.close();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate statement PDF", ex);
        }

        return out.toByteArray();
    }

    private void addHeader(Document document, StatementResponse statement) {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND);
        Paragraph title = new Paragraph("Nexus Bank", titleFont);
        document.add(title);

        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 13, Color.DARK_GRAY);
        document.add(new Paragraph("Account statement", subtitleFont));

        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
        document.add(new Paragraph("Account #" + statement.getAccountId(), metaFont));
        document.add(new Paragraph(
                "Period: " + formatDate(statement.getFromDate()) + "  —  " + formatDate(statement.getToDate()),
                metaFont));
        document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DATETIME_FMT), metaFont));
        document.add(new Paragraph(" "));
    }

    private void addSummary(Document document, StatementResponse statement) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8f);
        table.setSpacingAfter(16f);

        addSummaryCell(table, "Opening balance", formatAmount(statement.getOpeningBalance()), Color.BLACK);
        addSummaryCell(table, "Total deposited", "+ " + formatAmount(statement.getTotalDeposited()),
                new Color(22, 163, 74));
        addSummaryCell(table, "Total withdrawn", "- " + formatAmount(statement.getTotalWithdrawn()),
                new Color(220, 38, 38));
        addSummaryCell(table, "Closing balance", formatAmount(statement.getClosingBalance()), BRAND);

        document.add(table);
    }

    private void addSummaryCell(PdfPTable table, String label, String value, Color valueColor) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, valueColor);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setPadding(6f);
        cell.setBackgroundColor(ROW_ALT);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valueFont));
        table.addCell(cell);
    }

    private void addTransactions(Document document, StatementResponse statement) {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        int count = statement.getTransactions() != null ? statement.getTransactions().size() : 0;
        document.add(new Paragraph("Transactions (" + count + ")", sectionFont));
        document.add(new Paragraph(" "));

        if (count == 0) {
            Font muted = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY);
            document.add(new Paragraph("No transactions in this period.", muted));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{2.4f, 1.4f, 1.6f, 1.8f, 2.6f, 1.4f});
        table.setWidthPercentage(100);

        addHeaderCell(table, "Date");
        addHeaderCell(table, "Type");
        addHeaderCell(table, "Amount");
        addHeaderCell(table, "Balance after");
        addHeaderCell(table, "Counterparty");
        addHeaderCell(table, "Status");

        boolean alt = false;
        for (TransactionResponse tx : statement.getTransactions()) {
            Color bg = alt ? ROW_ALT : Color.WHITE;
            addBodyCell(table, formatDateTime(tx.getCreatedAt()), bg, Element.ALIGN_LEFT);
            addBodyCell(table, tx.getType(), bg, Element.ALIGN_LEFT);
            addBodyCell(table, formatAmount(tx.getAmount()) + " " + nullSafe(tx.getCurrency()), bg, Element.ALIGN_RIGHT);
            addBodyCell(table, formatAmount(tx.getBalanceAfter()), bg, Element.ALIGN_RIGHT);
            addBodyCell(table, nullSafe(tx.getCounterpartyIban()), bg, Element.ALIGN_LEFT);
            addBodyCell(table, tx.getStatus(), bg, Element.ALIGN_LEFT);
            alt = !alt;
        }

        document.add(table);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(6f);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Color background, int alignment) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(background);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private void addFooter(Document document) {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);
        Paragraph footer = new Paragraph(
                "This is a system-generated document and does not require a signature.", footerFont);
        footer.setSpacingBefore(20f);
        document.add(footer);
    }

    private String formatAmount(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "—" : value.format(DATE_FMT);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "—" : value.format(DATETIME_FMT);
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
