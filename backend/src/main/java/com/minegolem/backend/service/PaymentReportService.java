package com.minegolem.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.Payment;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.enums.PaymentMethod;
import com.minegolem.backend.domain.enums.PaymentType;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.PaymentRepository;
import com.minegolem.backend.security.StaffUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentReportService {

    private final PaymentRepository paymentRepository;
    private final GymRepository gymRepository;

    @Transactional(readOnly = true)
    public void generatePdfReport(
            LocalDate from,
            LocalDate to,
            PaymentType type,
            PaymentMethod method,
            HttpServletResponse response
    ) throws IOException, DocumentException {
        UUID gymId = currentGymId();
        Gym gym = gymRepository.findById(gymId).orElseThrow();
        
        List<Payment> payments = paymentRepository.findByGymAndDateRange(gymId, from, to);
        
        // Filter by type
        if (type != null) {
            payments = payments.stream().filter(p -> p.getType() == type).toList();
        }
        // Filter by method
        if (method != null) {
            payments = payments.stream().filter(p -> p.getMethod() == method).toList();
        }

        // Set response headers for PDF download
        response.setContentType("application/pdf");
        String filename = String.format("report_incassi_%s_%s.pdf", from, to);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
        writer.setPageEvent(new PDFHeaderFooter(gym));
        
        document.open();
        
        // Define beautiful fonts
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 30, 36));
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(136, 136, 160));
        Font gymFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(232, 200, 74)); // Gym SaaS gold accent
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(30, 30, 36));
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(30, 30, 36));
        
        // 1. Header Section (Gym Name and Info)
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingAfter(10f);
        
        // Col widths
        headerTable.setWidths(new float[]{1.2f, 0.8f});
        
        // Gym Cell
        PdfPCell gymCell = new PdfPCell();
        gymCell.setBorder(PdfPCell.NO_BORDER);
        gymCell.addElement(new Paragraph(gym.getName().toUpperCase(), gymFont));
        if (gym.getAddress() != null) {
            gymCell.addElement(new Paragraph(gym.getAddress(), subtitleFont));
        }
        if (gym.getPhone() != null || gym.getEmail() != null) {
            String contact = (gym.getPhone() != null ? gym.getPhone() : "") + 
                             (gym.getPhone() != null && gym.getEmail() != null ? " | " : "") + 
                             (gym.getEmail() != null ? gym.getEmail() : "");
            gymCell.addElement(new Paragraph(contact, subtitleFont));
        }
        headerTable.addCell(gymCell);
        
        // Title Cell
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(PdfPCell.NO_BORDER);
        
        Paragraph titleP = new Paragraph("REPORT CASSA", titleFont);
        titleP.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(titleP);
        
        Paragraph periodP = new Paragraph("Periodo: " + from.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " - " + to.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), subtitleFont);
        periodP.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(periodP);
        
        headerTable.addCell(titleCell);
        document.add(headerTable);
        
        // Add beautiful solid gold line
        PdfPTable lineTable = new PdfPTable(1);
        lineTable.setWidthPercentage(100);
        lineTable.setSpacingAfter(15f);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBackgroundColor(new Color(232, 200, 74));
        lineCell.setBorder(PdfPCell.NO_BORDER);
        lineCell.setFixedHeight(2f);
        lineTable.addCell(lineCell);
        document.add(lineTable);

        // 2. Summary Cards
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        
        for (Payment p : payments) {
            if (p.getType() == PaymentType.EXPENSE) {
                totalExpense = totalExpense.add(p.getAmount());
            } else {
                totalIncome = totalIncome.add(p.getAmount());
            }
        }
        BigDecimal netTotal = totalIncome.subtract(totalExpense);
        
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.ITALY);
        
        PdfPTable summaryTable = new PdfPTable(3);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(20f);
        summaryTable.setWidths(new float[]{1f, 1f, 1f});
        
        // Income Card
        PdfPCell incassiCard = new PdfPCell();
        incassiCard.setBackgroundColor(new Color(240, 253, 250)); // Light green-teal
        incassiCard.setBorderColor(new Color(209, 250, 229));
        incassiCard.setBorderWidth(1f);
        incassiCard.setPadding(10f);
        incassiCard.addElement(new Paragraph("TOTALE INCASSI", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(5, 150, 105))));
        incassiCard.addElement(new Paragraph(currencyFormat.format(totalIncome), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(4, 120, 87))));
        summaryTable.addCell(incassiCard);
        
        // Expense Card
        PdfPCell usciteCard = new PdfPCell();
        usciteCard.setBackgroundColor(new Color(254, 242, 242)); // Light red
        usciteCard.setBorderColor(new Color(254, 226, 226));
        usciteCard.setBorderWidth(1f);
        usciteCard.setPadding(10f);
        usciteCard.addElement(new Paragraph("TOTALE USCITE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(220, 38, 38))));
        usciteCard.addElement(new Paragraph(currencyFormat.format(totalExpense), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(185, 28, 28))));
        summaryTable.addCell(usciteCard);
        
        // Net Card
        PdfPCell nettoCard = new PdfPCell();
        nettoCard.setBackgroundColor(new Color(254, 252, 232)); // Light gold
        nettoCard.setBorderColor(new Color(254, 249, 195));
        nettoCard.setBorderWidth(1f);
        nettoCard.setPadding(10f);
        nettoCard.addElement(new Paragraph("SALDO NETTO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(161, 98, 7))));
        nettoCard.addElement(new Paragraph(currencyFormat.format(netTotal), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(113, 63, 18))));
        summaryTable.addCell(nettoCard);
        
        document.add(summaryTable);
        
        // 3. Transactions Table
        PdfPTable pdfTable = new PdfPTable(6);
        pdfTable.setWidthPercentage(100);
        pdfTable.setWidths(new float[]{1.4f, 1.1f, 2.5f, 1.4f, 3.2f, 1.4f});
        
        // Headers
        String[] headers = {"DATA", "TIPO", "CLIENTE", "METODO", "NOTE", "IMPORTO"};
        Color headerBg = new Color(30, 30, 36);
        
        for (String h : headers) {
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(headerBg);
            cell.setPadding(6f);
            cell.setBorderColor(new Color(42, 42, 53));
            
            Paragraph p = new Paragraph(h, headerFont);
            if (h.equals("IMPORTO")) {
                p.setAlignment(Element.ALIGN_RIGHT);
            } else {
                p.setAlignment(Element.ALIGN_LEFT);
            }
            cell.addElement(p);
            pdfTable.addCell(cell);
        }
        
        // Rows
        boolean alternate = false;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        
        for (Payment p : payments) {
            Color rowBg = alternate ? new Color(249, 250, 251) : Color.WHITE;
            alternate = !alternate;
            Color borderColor = new Color(229, 231, 235);
            
            // Date
            PdfPCell cDate = new PdfPCell();
            cDate.setBackgroundColor(rowBg);
            cDate.setPadding(6f);
            cDate.setBorderColor(borderColor);
            cDate.addElement(new Paragraph(p.getPaymentDate().format(dtf), cellFont));
            pdfTable.addCell(cDate);
            
            // Type
            String typeStr = p.getType() == PaymentType.EXPENSE ? "Uscita" : "Incasso";
            Color typeColor = p.getType() == PaymentType.EXPENSE ? new Color(220, 38, 38) : new Color(5, 150, 105);
            Font typeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, typeColor);
            PdfPCell cType = new PdfPCell();
            cType.setBackgroundColor(rowBg);
            cType.setPadding(6f);
            cType.setBorderColor(borderColor);
            cType.addElement(new Paragraph(typeStr, typeFont));
            pdfTable.addCell(cType);
            
            // Client
            User user = p.getUser();
            String clientName = user != null ? user.getFullName() : "—";
            PdfPCell cClient = new PdfPCell();
            cClient.setBackgroundColor(rowBg);
            cClient.setPadding(6f);
            cClient.setBorderColor(borderColor);
            cClient.addElement(new Paragraph(clientName, cellFont));
            pdfTable.addCell(cClient);
            
            // Method
            String methodStr = p.getMethod() == PaymentMethod.CASH ? "Contanti" : 
                              p.getMethod() == PaymentMethod.CARD ? "Carta" : "Bonifico";
            PdfPCell cMethod = new PdfPCell();
            cMethod.setBackgroundColor(rowBg);
            cMethod.setPadding(6f);
            cMethod.setBorderColor(borderColor);
            cMethod.addElement(new Paragraph(methodStr, cellFont));
            pdfTable.addCell(cMethod);
            
            // Notes
            String notesStr = p.getNotes() != null ? p.getNotes() : "—";
            PdfPCell cNotes = new PdfPCell();
            cNotes.setBackgroundColor(rowBg);
            cNotes.setPadding(6f);
            cNotes.setBorderColor(borderColor);
            cNotes.addElement(new Paragraph(notesStr, cellFont));
            pdfTable.addCell(cNotes);
            
            // Amount
            String amountStr = (p.getType() == PaymentType.EXPENSE ? "-" : "") + currencyFormat.format(p.getAmount());
            Font amountFont = p.getType() == PaymentType.EXPENSE 
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(220, 38, 38))
                : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(5, 150, 105));
            PdfPCell cAmount = new PdfPCell();
            cAmount.setBackgroundColor(rowBg);
            cAmount.setPadding(6f);
            cAmount.setBorderColor(borderColor);
            Paragraph amountP = new Paragraph(amountStr, amountFont);
            amountP.setAlignment(Element.ALIGN_RIGHT);
            cAmount.addElement(amountP);
            pdfTable.addCell(cAmount);
        }
        
        // Total row
        PdfPCell cTotalLabel = new PdfPCell();
        cTotalLabel.setColspan(5);
        cTotalLabel.setBackgroundColor(new Color(243, 244, 246));
        cTotalLabel.setPadding(8f);
        cTotalLabel.setBorderColor(new Color(209, 213, 219));
        Paragraph totalLabelP = new Paragraph("TOTALE GENERALE NETTO", totalFont);
        totalLabelP.setAlignment(Element.ALIGN_RIGHT);
        cTotalLabel.addElement(totalLabelP);
        pdfTable.addCell(cTotalLabel);
        
        String netTotalStr = currencyFormat.format(netTotal);
        Font netTotalFont = netTotal.compareTo(BigDecimal.ZERO) >= 0
            ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(5, 150, 105))
            : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(220, 38, 38));
        PdfPCell cTotalVal = new PdfPCell();
        cTotalVal.setBackgroundColor(new Color(243, 244, 246));
        cTotalVal.setPadding(8f);
        cTotalVal.setBorderColor(new Color(209, 213, 219));
        Paragraph totalValP = new Paragraph(netTotalStr, netTotalFont);
        totalValP.setAlignment(Element.ALIGN_RIGHT);
        cTotalVal.addElement(totalValP);
        pdfTable.addCell(cTotalVal);
        
        document.add(pdfTable);
        
        document.close();
    }
    
    private UUID currentGymId() {
        StaffUserDetails details = (StaffUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return details.getGymId();
    }

    // PDF Page Event Handler for Header & Footer
    private static class PDFHeaderFooter extends PdfPageEventHelper {
        private final Gym gym;
        private final Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(156, 163, 175));

        public PDFHeaderFooter(Gym gym) {
            this.gym = gym;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            
            // Draw thin gray line above footer
            cb.setColorStroke(new Color(229, 231, 235));
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), document.bottom() - 10);
            cb.lineTo(document.right(), document.bottom() - 10);
            cb.stroke();

            // Footer text
            String footerText = String.format("%s - Generato il %s", 
                gym.getName(), 
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );
            String pageText = String.format("Pagina %d", writer.getPageNumber());

            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, 
                new Phrase(footerText, footerFont), 
                document.left(), document.bottom() - 22, 0);

            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, 
                new Phrase(pageText, footerFont), 
                document.right(), document.bottom() - 22, 0);
        }
    }
}
