package com.minegolem.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.minegolem.backend.domain.entity.*;
import com.minegolem.backend.domain.enums.ReportType;
import com.minegolem.backend.repository.*;
import com.minegolem.backend.security.StaffUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color GOLD = new Color(232, 200, 74);
    private static final Color DARK = new Color(30, 30, 36);

    private final GymRepository gymRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MedicalCertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final AccessRepository accessRepository;
    private final VoucherRepository voucherRepository;

    @Transactional(readOnly = true)
    public void generate(ReportType type, LocalDate from, LocalDate to,
                         HttpServletResponse response) throws IOException, DocumentException {
        validatePeriod(type, from, to);
        UUID gymId = currentGymId();
        Gym gym = gymRepository.findById(gymId).orElseThrow();
        ReportData data = loadData(type, gymId, from, to);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename(type, from, to) + "\"");

        Document document = new Document(PageSize.A4.rotate(), 32, 32, 50, 38);
        PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
        writer.setPageEvent(new Footer(gym.getName()));
        document.open();
        addHeader(document, gym, data.title(), data.periodLabel());
        addSummary(document, data.rows().size());
        addTable(document, data.headers(), data.rows());
        document.close();
    }

    private ReportData loadData(ReportType type, UUID gymId, LocalDate from, LocalDate to) {
        return switch (type) {
            case SUBSCRIPTIONS_EXPIRED -> subscriptionData("ABBONAMENTI SCADUTI", from, to,
                subscriptionRepository.findForExpiryReport(gymId, from, to), true);
            case SUBSCRIPTIONS_STARTED -> subscriptionData("ABBONAMENTI ATTIVATI", from, to,
                subscriptionRepository.findForStartReport(gymId, from, to), false);
            case CERTIFICATES_EXPIRING -> certificateData("SCADENZE CERTIFICATI", from, to,
                certificateRepository.findForExpiryReport(gymId, from, to));
            case CERTIFICATES_VALID -> certificateData("CERTIFICATI VALIDI", null, null,
                certificateRepository.findLatestValidForReport(gymId, LocalDate.now()));
            case CERTIFICATES_MISSING -> missingCertificatesData(
                userRepository.findWithoutValidCertificate(gymId, LocalDate.now()));
            case CLIENTS -> clientsData(userRepository.findByGymIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(gymId));
            case ACCESSES -> accessesData(from, to, accessRepository.findByGymAndTimeRange(
                gymId, from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1)));
            case VOUCHERS -> vouchersData(voucherRepository.findForReport(gymId));
        };
    }

    private ReportData vouchersData(List<Voucher> vouchers) {
        List<List<String>> rows = vouchers.stream().map(v -> List.of(
            code(v.getUser()), v.getUser().getFullName(), value(v.getName()), value(v.getCode()),
            euro(v.getCost()), format(v.getStartDate()), format(v.getEndDate())
        )).toList();
        return new ReportData("VOUCHER CLIENTI", "Situazione al " + format(LocalDate.now()),
            List.of("Codice cliente", "Cliente", "Nome voucher", "Codice voucher", "Costo", "Inizio", "Fine"), rows);
    }

    private ReportData subscriptionData(String title, LocalDate from, LocalDate to,
                                        List<Subscription> items, boolean expiryReport) {
        List<List<String>> rows = items.stream().map(s -> List.of(
            code(s.getUser()), s.getUser().getFullName(), s.getSubscriptionType().getName(),
            format(s.getStartDate()), format(s.getEndDate()), euro(s.getPrice())
        )).toList();
        return new ReportData(title, period(from, to),
            List.of("Codice", "Cliente", "Abbonamento", "Inizio", expiryReport ? "Scadenza" : "Fine", "Prezzo"), rows);
    }

    private ReportData certificateData(String title, LocalDate from, LocalDate to,
                                       List<MedicalCertificate> items) {
        List<List<String>> rows = items.stream().map(c -> List.of(
            code(c.getUser()), c.getUser().getFullName(), format(c.getIssuedDate()),
            format(c.getExpiryDate()), certificateStatus(c)
        )).toList();
        return new ReportData(title, from == null ? "Situazione al " + format(LocalDate.now()) : period(from, to),
            List.of("Codice", "Cliente", "Emissione", "Scadenza", "Stato"), rows);
    }

    private ReportData missingCertificatesData(List<User> users) {
        List<List<String>> rows = users.stream().map(u -> List.of(
            code(u), u.getFullName(), value(u.getPhone()), value(u.getEmail()), u.isActive() ? "Attivo" : "Inattivo"
        )).toList();
        return new ReportData("CERTIFICATI MANCANTI O SCADUTI", "Situazione al " + format(LocalDate.now()),
            List.of("Codice", "Cliente", "Telefono", "Email", "Cliente"), rows);
    }

    private ReportData clientsData(List<User> users) {
        List<List<String>> rows = users.stream().map(u -> List.of(
            code(u), u.getFullName(), value(u.getPhone()), value(u.getEmail()),
            format(u.getBirthDate()), u.isActive() ? "Attivo" : "Inattivo"
        )).toList();
        return new ReportData("ELENCO CLIENTI", "Situazione al " + format(LocalDate.now()),
            List.of("Codice", "Cliente", "Telefono", "Email", "Nascita", "Stato"), rows);
    }

    private ReportData accessesData(LocalDate from, LocalDate to, List<Access> accesses) {
        List<List<String>> rows = new ArrayList<>();
        for (Access a : accesses) {
            rows.add(List.of(a.getAccessTime().format(DATE_TIME),
                a.getUser() == null ? "—" : a.getUser().getFullName(),
                value(a.getNfcTagUid()), a.isGranted() ? "Consentito" : "Negato",
                a.getDenialReason() == null ? "—" : a.getDenialReason().name()));
        }
        return new ReportData("REGISTRO ACCESSI", period(from, to),
            List.of("Data e ora", "Cliente", "Tag NFC", "Esito", "Motivo"), rows);
    }

    private void addHeader(Document document, Gym gym, String title, String period) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1, 1});
        table.setWidthPercentage(100);
        PdfPCell left = noBorder();
        left.addElement(new Paragraph(gym.getName().toUpperCase(), font(13, Font.BOLD, GOLD)));
        left.addElement(new Paragraph(value(gym.getAddress()), font(9, Font.NORMAL, Color.GRAY)));
        table.addCell(left);
        PdfPCell right = noBorder();
        Paragraph heading = new Paragraph(title, font(18, Font.BOLD, DARK));
        heading.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(heading);
        Paragraph subtitle = new Paragraph(period, font(9, Font.NORMAL, Color.GRAY));
        subtitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(subtitle);
        table.addCell(right);
        table.setSpacingAfter(8);
        document.add(table);
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(GOLD);
        cell.setFixedHeight(2);
        line.addCell(cell);
        line.setSpacingAfter(12);
        document.add(line);
    }

    private void addSummary(Document document, int count) throws DocumentException {
        Paragraph p = new Paragraph("Totale risultati: " + count, font(10, Font.BOLD, DARK));
        p.setSpacingAfter(10);
        document.add(p);
    }

    private void addTable(Document document, List<String> headers, List<List<String>> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font(9, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(DARK);
            cell.setPadding(7);
            table.addCell(cell);
        }
        if (rows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Nessun risultato per i filtri selezionati.", font(10, Font.NORMAL, Color.GRAY)));
            empty.setColspan(headers.size());
            empty.setPadding(16);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(empty);
        } else {
            int index = 0;
            for (List<String> row : rows) {
                for (String value : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, font(8, Font.NORMAL, DARK)));
                    cell.setPadding(6);
                    cell.setBorderColor(new Color(225, 225, 230));
                    if (index % 2 == 1) cell.setBackgroundColor(new Color(248, 248, 250));
                    table.addCell(cell);
                }
                index++;
            }
        }
        document.add(table);
    }

    private void validatePeriod(ReportType type, LocalDate from, LocalDate to) {
        boolean required = type == ReportType.SUBSCRIPTIONS_EXPIRED
            || type == ReportType.SUBSCRIPTIONS_STARTED
            || type == ReportType.CERTIFICATES_EXPIRING
            || type == ReportType.ACCESSES;
        if (required && (from == null || to == null)) throw new IllegalArgumentException("Il periodo è obbligatorio per questo report");
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("La data iniziale non può superare quella finale");
    }

    private UUID currentGymId() {
        StaffUserDetails details = (StaffUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return details.getGymId();
    }

    private static Font font(float size, int style, Color color) { return FontFactory.getFont(FontFactory.HELVETICA, size, style, color); }
    private static PdfPCell noBorder() { PdfPCell c = new PdfPCell(); c.setBorder(Rectangle.NO_BORDER); return c; }
    private static String period(LocalDate from, LocalDate to) { return "Periodo: " + format(from) + " - " + format(to); }
    private static String format(LocalDate date) { return date == null ? "—" : date.format(DATE); }
    private static String value(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static String code(User user) { return user.getClientCode() == null ? "—" : String.valueOf(user.getClientCode()); }
    private static String euro(java.math.BigDecimal amount) { return amount == null ? "—" : String.format(java.util.Locale.ITALY, "€ %.2f", amount); }
    private static String certificateStatus(MedicalCertificate c) {
        if (c.getExpiryDate().isBefore(LocalDate.now())) return "Scaduto";
        if (!c.getExpiryDate().isAfter(LocalDate.now().plusDays(30))) return "In scadenza";
        return "Valido";
    }
    private static String filename(ReportType type, LocalDate from, LocalDate to) {
        String suffix = from != null && to != null ? "_" + from + "_" + to : "_" + LocalDate.now();
        return "report_" + type.name().toLowerCase() + suffix + ".pdf";
    }

    private record ReportData(String title, String periodLabel, List<String> headers, List<List<String>> rows) {}

    private static class Footer extends PdfPageEventHelper {
        private final String gymName;
        Footer(String gymName) { this.gymName = gymName; }
        @Override public void onEndPage(PdfWriter writer, Document document) {
            Font footer = font(8, Font.NORMAL, Color.GRAY);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                new Phrase(gymName + " — Report gestionale", footer), document.left(), document.bottom() - 20, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                new Phrase("Pagina " + writer.getPageNumber(), footer), document.right(), document.bottom() - 20, 0);
        }
    }
}
