package com.minegolem.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.minegolem.backend.domain.entity.*;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientDossierPdfService {

    private final UserRepository users;
    private final GymRepository gyms;
    private final ClientDossierProfileRepository profileRepo;
    private final ClientDossierProgressRepository progressRepo;
    private final ClientDossierProgramRepository programRepo;
    private final ClientDossierNoteRepository notesRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color PRIMARY_DARK = new Color(20, 20, 28);
    private static final Color ACCENT_GOLD = new Color(212, 175, 55);
    private static final Color BG_LIGHT = new Color(248, 249, 250);
    private static final Color BORDER_GRAY = new Color(225, 228, 232);
    private static final Color TEXT_DARK = new Color(35, 35, 45);
    private static final Color TEXT_MUTED = new Color(105, 110, 120);

    @Transactional(readOnly = true)
    public void generate(
        UUID userId,
        LocalDate fromDate,
        LocalDate toDate,
        String month,
        boolean includeAssessment,
        boolean includeProgress,
        boolean includePrograms,
        boolean includeNotes,
        HttpServletResponse response
    ) throws IOException, DocumentException {
        UUID gymId = gymId();
        User u = users.findByIdAndGymIdAndDeletedAtIsNull(userId, gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        Gym gym = gyms.findById(gymId).orElseThrow();

        if (month != null && !month.isBlank()) {
            try {
                YearMonth ym = YearMonth.parse(month.trim());
                fromDate = ym.atDay(1);
                toDate = ym.atEndOfMonth();
            } catch (Exception ignored) {}
        }

        response.setContentType("application/pdf");
        String filename = "dossier_cliente_" + (u.getClientCode() != null ? u.getClientCode() : u.getId().toString().substring(0, 8)) + ".pdf";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        Document doc = new Document(PageSize.A4, 36, 36, 40, 36);
        PdfWriter.getInstance(doc, response.getOutputStream());
        doc.open();

        Font fontGym = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, ACCENT_GOLD);
        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PRIMARY_DARK);
        Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
        Font fontSecTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_DARK);
        Font fontSubSec = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PRIMARY_DARK);
        Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, TEXT_DARK);
        Font fontBodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, TEXT_DARK);
        Font fontSmall = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, TEXT_MUTED);

        // Header
        Paragraph pGym = new Paragraph(gym.getName().toUpperCase(), fontGym);
        doc.add(pGym);
        Paragraph pTitle = new Paragraph("DOSSIER DI MONITORAGGIO & PERCORSO", fontTitle);
        pTitle.setSpacingAfter(2);
        doc.add(pTitle);

        String periodStr = "Storico Completo";
        if (fromDate != null && toDate != null) {
            periodStr = "Periodo: " + fromDate.format(DATE_FMT) + " - " + toDate.format(DATE_FMT);
        } else if (fromDate != null) {
            periodStr = "A partire dal: " + fromDate.format(DATE_FMT);
        } else if (toDate != null) {
            periodStr = "Fino al: " + toDate.format(DATE_FMT);
        }
        Paragraph pSub = new Paragraph("Generato il: " + LocalDate.now().format(DATE_FMT) + "   |   " + periodStr, fontSubtitle);
        pSub.setSpacingAfter(14);
        doc.add(pSub);

        // 1. Anagrafica & Obiettivi
        ClientDossierProfile profile = profileRepo.findByUserId(userId).orElse(null);
        LocalDate firstReg = u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate() : LocalDate.now();

        PdfPTable clientTable = new PdfPTable(2);
        clientTable.setWidthPercentage(100);
        clientTable.setWidths(new float[]{1f, 1f});
        clientTable.setSpacingAfter(12);

        PdfPCell c1 = createSectionBox("ANAGRAFICA CLIENTE", fontSecTitle);
        c1.addElement(new Paragraph("Nome: " + u.getFullName(), fontBodyBold));
        c1.addElement(new Paragraph("Codice Cliente: #" + (u.getClientCode() != null ? u.getClientCode() : "—"), fontBody));
        c1.addElement(new Paragraph("Email: " + dash(u.getEmail()) + "   Tel: " + dash(u.getPhone()), fontBody));
        c1.addElement(new Paragraph("Data Prima Iscrizione: " + firstReg.format(DATE_FMT), fontBodyBold));
        clientTable.addCell(c1);

        PdfPCell c2 = createSectionBox("OBIETTIVI & ESPERIENZA", fontSecTitle);
        String mainGoal = profile != null && profile.getMainGoal() != null ? profile.getMainGoal() : "Non specificato";
        String secGoals = profile != null && profile.getSecondaryGoals() != null ? profile.getSecondaryGoals() : "—";
        String expLevel = profile != null && profile.getExperienceLevel() != null ? profile.getExperienceLevel().name() : "Non specificato";

        c2.addElement(new Paragraph("Obiettivo Principale: " + mainGoal, fontBodyBold));
        c2.addElement(new Paragraph("Obiettivi Secondari: " + secGoals, fontBody));
        c2.addElement(new Paragraph("Livello Esperienza: " + expLevel, fontBodyBold));
        clientTable.addCell(c2);

        doc.add(clientTable);

        // 2. Valutazione Iniziale
        if (includeAssessment && profile != null) {
            PdfPTable assessTable = new PdfPTable(1);
            assessTable.setWidthPercentage(100);
            assessTable.setSpacingAfter(12);

            PdfPCell ac = createSectionBox("VALUTAZIONE INIZIALE", fontSecTitle);
            String initialW = profile.getInitialWeight() != null ? profile.getInitialWeight() + " kg" : "—";
            String initialH = profile.getInitialHeight() != null ? profile.getInitialHeight() + " cm" : "—";
            String initialCirc = profile.getInitialMeasurements() != null ? profile.getInitialMeasurements() : "—";
            String initialAssess = profile.getInitialAssessment() != null ? profile.getInitialAssessment() : "—";
            String initialLimits = profile.getInitialLimitations() != null ? profile.getInitialLimitations() : "Nessuna segnalata";

            ac.addElement(new Paragraph("Parametri Fisici: Peso: " + initialW + "   |   Altezza: " + initialH, fontBodyBold));
            ac.addElement(new Paragraph("Circonferenze Iniziali: " + initialCirc, fontBody));
            ac.addElement(new Paragraph("Valutazione Scritta: " + initialAssess, fontBody));
            ac.addElement(new Paragraph("Limitazioni Segnalate: " + initialLimits, fontBody));
            assessTable.addCell(ac);
            doc.add(assessTable);
        }

        // 3. Programmazione
        if (includePrograms) {
            List<ClientDossierProgram> progs = programRepo.findByUserIdAndDeletedAtIsNullOrderByStartDateDescCreatedAtDesc(userId);
            if (!progs.isEmpty()) {
                Paragraph pProgTitle = new Paragraph("PROGRAMMAZIONE SCHEDE DI ALLENAMENTO", fontSecTitle);
                pProgTitle.setSpacingAfter(6);
                doc.add(pProgTitle);

                PdfPTable tProg = new PdfPTable(4);
                tProg.setWidthPercentage(100);
                tProg.setWidths(new float[]{1.5f, 1.5f, 3.5f, 2f});
                tProg.setSpacingAfter(12);

                addHeaderCell(tProg, "Data Inizio", fontSubSec);
                addHeaderCell(tProg, "Data Revisione", fontSubSec);
                addHeaderCell(tProg, "Modifiche Effettuate", fontSubSec);
                addHeaderCell(tProg, "Coach Responsabile", fontSubSec);

                for (ClientDossierProgram prog : progs) {
                    addBodyCell(tProg, prog.getStartDate() != null ? prog.getStartDate().format(DATE_FMT) : "—", fontBody);
                    addBodyCell(tProg, prog.getReviewDate() != null ? prog.getReviewDate().format(DATE_FMT) : "—", fontBody);
                    addBodyCell(tProg, dash(prog.getChangesMade()), fontBody);
                    addBodyCell(tProg, dash(prog.getCoachName()), fontBody);
                }
                doc.add(tProg);
            }
        }

        // 4. Monitoraggio & Controlli Periodici
        if (includeProgress) {
            List<ClientDossierProgress> progressList = progressRepo.findByUserIdAndDeletedAtIsNullOrderByRecordedDateDescCreatedAtDesc(userId);
            final LocalDate fFrom = fromDate;
            final LocalDate fTo = toDate;
            List<ClientDossierProgress> filtered = progressList.stream().filter(p -> {
                if (fFrom != null && p.getRecordedDate().isBefore(fFrom)) return false;
                if (fTo != null && p.getRecordedDate().isAfter(fTo)) return false;
                return true;
            }).toList();

            Paragraph pMonTitle = new Paragraph("STORICO DEI CONTROLLI & MONITORAGGIO (" + filtered.size() + " Rilevazioni)", fontSecTitle);
            pMonTitle.setSpacingAfter(6);
            doc.add(pMonTitle);

            if (filtered.isEmpty()) {
                Paragraph emptyP = new Paragraph("Nessun controllo registrato nel periodo selezionato.", fontSubtitle);
                emptyP.setSpacingAfter(10);
                doc.add(emptyP);
            } else {
                for (ClientDossierProgress pr : filtered) {
                    PdfPTable card = new PdfPTable(1);
                    card.setWidthPercentage(100);
                    card.setSpacingAfter(10);

                    String nextDateStr = pr.getNextCheckDate() != null ? "   |   Prossimo Controllo: " + pr.getNextCheckDate().format(DATE_FMT) : "";
                    PdfPCell cell = createSectionBox("Controllo del " + pr.getRecordedDate().format(DATE_FMT) + nextDateStr, fontSubSec);

                    StringBuilder metrics = new StringBuilder();
                    if (pr.getWeight() != null) metrics.append("Peso: ").append(pr.getWeight()).append(" kg   ");
                    if (pr.getHeight() != null) metrics.append("Altezza: ").append(pr.getHeight()).append(" cm   ");
                    if (pr.getBodyFatPercentage() != null) metrics.append("Massa Grassa: ").append(pr.getBodyFatPercentage()).append("%   ");
                    if (pr.getMuscleMass() != null) metrics.append("Massa Muscolare: ").append(pr.getMuscleMass()).append(" kg");

                    if (metrics.length() > 0) {
                        cell.addElement(new Paragraph(metrics.toString(), fontBodyBold));
                    }
                    if (pr.getMeasurements() != null && !pr.getMeasurements().isBlank()) {
                        cell.addElement(new Paragraph("Circonferenze: " + pr.getMeasurements(), fontBody));
                    }
                    if (pr.getProgressNotes() != null && !pr.getProgressNotes().isBlank()) {
                        cell.addElement(new Paragraph("Progressi Rilevati: " + pr.getProgressNotes(), fontBody));
                    }
                    if (pr.getPerformanceNotes() != null && !pr.getPerformanceNotes().isBlank()) {
                        cell.addElement(new Paragraph("Carichi / Performance: " + pr.getPerformanceNotes(), fontBody));
                    }
                    if (pr.getCoachNotes() != null && !pr.getCoachNotes().isBlank()) {
                        cell.addElement(new Paragraph("Note del Coach: " + pr.getCoachNotes(), fontBody));
                    }
                    if (pr.getCriticalIssues() != null && !pr.getCriticalIssues().isBlank()) {
                        cell.addElement(new Paragraph("Criticità Rilevate: " + pr.getCriticalIssues(), fontBody));
                    }
                    if (pr.getChangesMade() != null && !pr.getChangesMade().isBlank()) {
                        cell.addElement(new Paragraph("Modifiche Effettuate: " + pr.getChangesMade(), fontBody));
                    }

                    card.addCell(cell);
                    doc.add(card);
                }
            }
        }

        // 5. Note libere del dossier
        if (includeNotes) {
            List<ClientDossierNote> noteList = notesRepo.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
            if (!noteList.isEmpty()) {
                Paragraph pNotesTitle = new Paragraph("NOTE DEL PERCORSO", fontSecTitle);
                pNotesTitle.setSpacingAfter(6);
                doc.add(pNotesTitle);

                for (ClientDossierNote n : noteList) {
                    Paragraph pn = new Paragraph(n.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + (n.getAuthor() != null ? " — " + n.getAuthor().getFullName() : ""), fontSmall);
                    doc.add(pn);
                    Paragraph pnc = new Paragraph(n.getContent(), fontBody);
                    pnc.setSpacingAfter(6);
                    doc.add(pnc);
                }
            }
        }

        // Footer
        Paragraph footer = new Paragraph("Documento riservato e confidenziale — GymFlow Legion System", fontSmall);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(15);
        doc.add(footer);

        doc.close();
    }

    private PdfPCell createSectionBox(String title, Font titleFont) {
        PdfPCell c = new PdfPCell();
        c.setPadding(9);
        c.setBackgroundColor(BG_LIGHT);
        c.setBorderColor(BORDER_GRAY);
        c.setBorderWidth(1f);
        Paragraph t = new Paragraph(title, titleFont);
        t.setSpacingAfter(5);
        c.addElement(t);
        return c;
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(6);
        c.setBackgroundColor(new Color(235, 238, 242));
        c.setBorderColor(BORDER_GRAY);
        table.addCell(c);
    }

    private void addBodyCell(PdfPTable table, String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(dash(text), font));
        c.setPadding(6);
        c.setBackgroundColor(Color.WHITE);
        c.setBorderColor(BORDER_GRAY);
        table.addCell(c);
    }

    private String dash(String s) {
        return s == null || s.isBlank() ? "—" : s.trim();
    }

    private UUID gymId() {
        return ((StaffUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getGymId();
    }
}
