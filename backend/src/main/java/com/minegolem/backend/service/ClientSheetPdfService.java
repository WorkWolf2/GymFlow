package com.minegolem.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.minegolem.backend.domain.entity.*;
import com.minegolem.backend.domain.enums.PaymentType;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.*;
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
public class ClientSheetPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_PAYMENTS_IN_PDF = 15;

    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final MedicalCertificateRepository medicalCertificateRepository;
    private final NfcTagRepository nfcTagRepository;

    @Transactional(readOnly = true)
    public void generatePdf(UUID userId, HttpServletResponse response) throws IOException, DocumentException {
        UUID gymId = currentGymId();
        User user = userRepository.findByIdAndGymIdAndDeletedAtIsNull(userId, gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        Gym gym = gymRepository.findById(gymId).orElseThrow();

        List<Subscription> subscriptions = subscriptionRepository
            .findByUserIdAndDeletedAtIsNullOrderByStartDateDesc(userId);
        List<Payment> payments = paymentRepository
            .findByUserIdAndDeletedAtIsNullOrderByPaymentDateDesc(userId);
        if (payments.size() > MAX_PAYMENTS_IN_PDF) {
            payments = payments.subList(0, MAX_PAYMENTS_IN_PDF);
        }

        var certOpt = medicalCertificateRepository
            .findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(userId);
        String nfcUid = nfcTagRepository.findByUserIdAndActiveTrue(userId)
            .map(NfcTag::getTagUid)
            .orElse(null);

        String filename = buildFilename(user);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 30, 36));
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(136, 136, 160));
        Font gymFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(232, 200, 74));
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(30, 30, 36));
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(100, 100, 120));
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(30, 30, 36));
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(30, 30, 36));

        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
        writer.setPageEvent(new SheetFooter(gym));
        document.open();

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1.2f, 0.8f});
        headerTable.setSpacingAfter(10f);

        PdfPCell gymCell = new PdfPCell();
        gymCell.setBorder(PdfPCell.NO_BORDER);
        gymCell.addElement(new Paragraph(gym.getName().toUpperCase(), gymFont));
        if (gym.getAddress() != null) {
            gymCell.addElement(new Paragraph(gym.getAddress(), subtitleFont));
        }
        headerTable.addCell(gymCell);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(PdfPCell.NO_BORDER);
        Paragraph titleP = new Paragraph("SCHEDA CLIENTE", titleFont);
        titleP.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(titleP);
        Paragraph dateP = new Paragraph(
            "Generato il " + LocalDate.now().format(DATE_FMT),
            subtitleFont
        );
        dateP.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(dateP);
        headerTable.addCell(titleCell);
        document.add(headerTable);

        addGoldLine(document);

        document.add(new Paragraph(user.getFullName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(30, 30, 36))));
        document.add(new Paragraph(
            "ID cliente #" + user.getClientCode(),
            subtitleFont
        ));
        document.add(Chunk.NEWLINE);

        document.add(new Paragraph("Anagrafica", sectionFont));
        document.add(spacer(4f));
        document.add(fieldTable(labelFont, valueFont,
            row("Codice cliente", "#" + user.getClientCode()),
            row("Nome", user.getFirstName()),
            row("Cognome", user.getLastName()),
            row("Codice fiscale", user.getFiscalCode()),
            row("Data di nascita", formatDate(user.getBirthDate())),
            row("Luogo di nascita", formatBirthPlace(user)),
            row("Sesso", formatSex(user.getSex())),
            row("Email", user.getEmail()),
            row("Telefono", user.getPhone()),
            row("Indirizzo", user.getAddress())
        ));
        document.add(spacer(8f));

        document.add(new Paragraph("Certificato medico e accesso", sectionFont));
        document.add(spacer(4f));
        MedicalCertificate cert = certOpt.orElse(null);
        document.add(fieldTable(labelFont, valueFont,
            row("Certificato", certStatusLabel(cert)),
            row("Emissione certificato", cert != null ? formatDate(cert.getIssuedDate()) : "—"),
            row("Scadenza certificato", cert != null ? formatDate(cert.getExpiryDate()) : "—"),
            row("Tag NFC", nfcUid != null ? nfcUid : "Non assegnato")
        ));
        document.add(spacer(8f));

        if (user.getNotes() != null && !user.getNotes().isBlank()) {
            document.add(new Paragraph("Note", sectionFont));
            document.add(spacer(4f));
            document.add(new Paragraph(user.getNotes().trim(), valueFont));
            document.add(spacer(8f));
        }

        document.add(new Paragraph("Abbonamenti", sectionFont));
        document.add(spacer(4f));
        if (subscriptions.isEmpty()) {
            document.add(new Paragraph("Nessun abbonamento registrato.", subtitleFont));
        } else {
            document.add(subscriptionsTable(subscriptions, headerFont, cellFont));
        }
        document.add(spacer(8f));

        document.add(new Paragraph("Ultimi pagamenti", sectionFont));
        if (payments.size() == MAX_PAYMENTS_IN_PDF) {
            document.add(new Paragraph(
                "(mostrati gli ultimi " + MAX_PAYMENTS_IN_PDF + " movimenti)",
                subtitleFont
            ));
        }
        document.add(spacer(4f));
        if (payments.isEmpty()) {
            document.add(new Paragraph("Nessun pagamento registrato.", subtitleFont));
        } else {
            document.add(paymentsTable(payments, headerFont, cellFont));
        }

        document.close();
    }

    private PdfPTable subscriptionsTable(List<Subscription> subscriptions, Font headerFont, Font cellFont)
        throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.2f, 1.2f, 1.2f, 1f, 1f});
        table.setSpacingBefore(2f);

        Color headerBg = new Color(45, 45, 55);
        addHeaderCell(table, "Piano", headerFont, headerBg);
        addHeaderCell(table, "Inizio", headerFont, headerBg);
        addHeaderCell(table, "Scadenza", headerFont, headerBg);
        addHeaderCell(table, "Prezzo", headerFont, headerBg);
        addHeaderCell(table, "Stato", headerFont, headerBg);

        for (Subscription sub : subscriptions) {
            String status = sub.isActive() ? "Attivo"
                : sub.isExpired() ? "Scaduto"
                : "Programmato";
            addCell(table, sub.getSubscriptionType().getName(), cellFont);
            addCell(table, formatDate(sub.getStartDate()), cellFont);
            addCell(table, formatDate(sub.getEndDate()), cellFont);
            addCell(table, formatMoney(sub.getPrice()), cellFont);
            addCell(table, status, cellFont);
        }
        return table;
    }

    private PdfPTable paymentsTable(List<Payment> payments, Font headerFont, Font cellFont)
        throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 1.2f, 1.4f, 2.2f});
        table.setSpacingBefore(2f);

        Color headerBg = new Color(45, 45, 55);
        addHeaderCell(table, "Data", headerFont, headerBg);
        addHeaderCell(table, "Importo", headerFont, headerBg);
        addHeaderCell(table, "Metodo", headerFont, headerBg);
        addHeaderCell(table, "Note", headerFont, headerBg);

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
        for (Payment payment : payments) {
            boolean expense = payment.getType() == PaymentType.EXPENSE;
            String amount = (expense ? "− " : "") + currency.format(payment.getAmount().abs());
            addCell(table, formatDate(payment.getPaymentDate()), cellFont);
            addCell(table, amount, cellFont);
            addCell(table, formatPaymentMethod(payment.getMethod()), cellFont);
            addCell(table, dash(payment.getNotes()), cellFont);
        }
        return table;
    }

    private PdfPTable fieldTable(Font labelFont, Font valueFont, String[]... rows) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2.2f});
        for (String[] row : rows) {
            PdfPCell labelCell = new PdfPCell(new Phrase(row[0], labelFont));
            labelCell.setBorder(PdfPCell.NO_BORDER);
            labelCell.setPaddingBottom(4f);
            table.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Phrase(dash(row[1]), valueFont));
            valueCell.setBorder(PdfPCell.NO_BORDER);
            valueCell.setPaddingBottom(4f);
            table.addCell(valueCell);
        }
        return table;
    }

    private static String[] row(String label, String value) {
        return new String[]{label, value};
    }

    private static void addGoldLine(Document document) throws DocumentException {
        PdfPTable lineTable = new PdfPTable(1);
        lineTable.setWidthPercentage(100);
        lineTable.setSpacingAfter(12f);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBackgroundColor(new Color(232, 200, 74));
        lineCell.setFixedHeight(3f);
        lineCell.setBorder(PdfPCell.NO_BORDER);
        lineTable.addCell(lineCell);
        document.add(lineTable);
    }

    private static Paragraph spacer(float after) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(after);
        return p;
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6f);
        cell.setBorderColor(new Color(229, 231, 235));
        table.addCell(cell);
    }

    private static void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", font));
        cell.setPadding(5f);
        cell.setBorderColor(new Color(229, 231, 235));
        table.addCell(cell);
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : null;
    }

    private static String formatBirthPlace(User user) {
        if (user.getBirthPlace() == null || user.getBirthPlace().isBlank()) {
            return null;
        }
        if (user.getBirthProvince() != null && !user.getBirthProvince().isBlank()) {
            return user.getBirthPlace() + " (" + user.getBirthProvince() + ")";
        }
        return user.getBirthPlace();
    }

    private static String formatSex(String sex) {
        if (sex == null || sex.isBlank()) {
            return null;
        }
        return switch (sex.toUpperCase(Locale.ROOT)) {
            case "M" -> "Maschile";
            case "F" -> "Femminile";
            default -> sex;
        };
    }

    private static String certStatusLabel(MedicalCertificate cert) {
        if (cert == null) {
            return "Assente";
        }
        return switch (cert.getStatus()) {
            case VALID -> "Valido";
            case EXPIRING_SOON -> "In scadenza";
            case EXPIRED -> "Scaduto";
            case MISSING -> "Assente";
        };
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        return NumberFormat.getCurrencyInstance(Locale.ITALY).format(amount);
    }

    private static String formatPaymentMethod(com.minegolem.backend.domain.enums.PaymentMethod method) {
        if (method == null) {
            return "—";
        }
        return switch (method) {
            case CASH -> "Contanti";
            case CARD -> "Carta";
            case TRANSFER -> "Bonifico";
        };
    }

    private static String dash(String value) {
        return value != null && !value.isBlank() ? value : "—";
    }

    private static String buildFilename(User user) {
        String lastName = user.getLastName() != null
            ? user.getLastName().replaceAll("[^a-zA-Z0-9_-]", "_")
            : "cliente";
        return String.format("scheda_cliente_%d_%s.pdf", user.getClientCode(), lastName);
    }

    private UUID currentGymId() {
        StaffUserDetails details = (StaffUserDetails)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return details.getGymId();
    }

    private static class SheetFooter extends PdfPageEventHelper {
        private final Gym gym;
        private final Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(156, 163, 175));

        SheetFooter(Gym gym) {
            this.gym = gym;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            cb.setColorStroke(new Color(229, 231, 235));
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), document.bottom() - 10);
            cb.lineTo(document.right(), document.bottom() - 10);
            cb.stroke();

            String footerText = gym.getName() + " — Scheda cliente";
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase(footerText, footerFont),
                document.left(), document.bottom() - 22, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                new Phrase("Pagina " + writer.getPageNumber(), footerFont),
                document.right(), document.bottom() - 22, 0);
        }
    }
}
