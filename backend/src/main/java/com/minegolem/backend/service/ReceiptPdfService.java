package com.minegolem.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.Receipt;
import com.minegolem.backend.domain.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class ReceiptPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale IT_LOCALE = Locale.ITALY;

    // Palette colori eleganti per la ricevuta
    private static final Color PRIMARY_COLOR = new Color(30, 41, 59); // Slate 800
    private static final Color ACCENT_COLOR = new Color(79, 70, 229);  // Indigo 600
    private static final Color TEXT_DARK = new Color(15, 23, 42);      // Slate 900
    private static final Color TEXT_MUTED = new Color(100, 116, 139);  // Slate 500
    private static final Color BG_LIGHT = new Color(248, 250, 252);    // Slate 50
    private static final Color BORDER_COLOR = new Color(226, 232, 240);// Slate 200
    private static final Color SUCCESS_COLOR = new Color(22, 101, 52); // Green 800
    private static final Color SUCCESS_BG = new Color(240, 253, 244);   // Green 50

    public byte[] generateReceiptPdf(Receipt receipt) throws IOException, DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 36);
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        document.open();

        Gym gym = receipt.getGym();
        User user = receipt.getUser();
        Map<String, Object> settings = gym.getSettings() != null ? gym.getSettings() : Map.of();

        // Font definitions
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, PRIMARY_COLOR);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, ACCENT_COLOR);
        Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_DARK);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_DARK);
        Font boldAccentFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ACCENT_COLOR);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
        Font smallItalicFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, TEXT_MUTED);
        Font amountFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, SUCCESS_COLOR);

        // ==========================================
        // 1. INTESTAZIONE: DATI ASD & TITOLO RICEVUTA
        // ==========================================
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1.3f, 0.9f});
        headerTable.setSpacingAfter(15f);

        // Dati ASD a sinistra
        PdfPCell asdCell = new PdfPCell();
        asdCell.setBorder(Rectangle.NO_BORDER);
        asdCell.setPadding(0);

        String asdName = getSettingString(settings, "asdName", gym.getName());
        String asdLegalAddress = getSettingString(settings, "asdLegalAddress", gym.getAddress());
        String asdFiscalCode = getSettingString(settings, "asdFiscalCode", "");
        String asdVatNumber = getSettingString(settings, "asdVatNumber", "");
        String asdRasdNumber = getSettingString(settings, "asdRasdNumber", "");
        String asdAffiliation = getSettingString(settings, "asdAffiliation", "");

        Paragraph pAsdName = new Paragraph(asdName.toUpperCase(), brandFont);
        pAsdName.setSpacingAfter(3f);
        asdCell.addElement(pAsdName);

        StringBuilder asdDetails = new StringBuilder();
        if (!asdLegalAddress.isBlank()) {
            asdDetails.append("Sede Legale: ").append(asdLegalAddress).append("\n");
        }
        if (!asdFiscalCode.isBlank()) {
            asdDetails.append("C.F.: ").append(asdFiscalCode);
            if (!asdVatNumber.isBlank()) {
                asdDetails.append(" - P.IVA: ").append(asdVatNumber);
            }
            asdDetails.append("\n");
        } else if (!asdVatNumber.isBlank()) {
            asdDetails.append("P.IVA: ").append(asdVatNumber).append("\n");
        }

        if (!asdRasdNumber.isBlank()) {
            asdDetails.append("Iscrizione Reg. Naz. Attività Sportive (RASD) n.: ").append(asdRasdNumber).append("\n");
        }
        if (!asdAffiliation.isBlank()) {
            asdDetails.append("Affiliazione: ").append(asdAffiliation).append("\n");
        }
        if (gym.getEmail() != null && !gym.getEmail().isBlank()) {
            asdDetails.append("Email: ").append(gym.getEmail());
            if (gym.getPhone() != null && !gym.getPhone().isBlank()) {
                asdDetails.append(" - Tel: ").append(gym.getPhone());
            }
            asdDetails.append("\n");
        }

        Paragraph pAsdDetails = new Paragraph(asdDetails.toString(), smallFont);
        pAsdDetails.setLeading(11f);
        asdCell.addElement(pAsdDetails);
        headerTable.addCell(asdCell);

        // Box Ricevuta a destra
        PdfPCell receiptCell = new PdfPCell();
        receiptCell.setBackgroundColor(BG_LIGHT);
        receiptCell.setBorderColor(BORDER_COLOR);
        receiptCell.setBorderWidth(1f);
        receiptCell.setPadding(10f);

        Paragraph pDocType = new Paragraph("RICEVUTA DI PAGAMENTO", titleFont);
        pDocType.setAlignment(Element.ALIGN_CENTER);
        receiptCell.addElement(pDocType);

        Paragraph pRecNumber = new Paragraph("N. " + receipt.getReceiptFormattedNumber(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, TEXT_DARK));
        pRecNumber.setAlignment(Element.ALIGN_CENTER);
        pRecNumber.setSpacingBefore(3f);
        receiptCell.addElement(pRecNumber);

        Paragraph pRecDate = new Paragraph("Data: " + receipt.getIssueDate().format(DATE_FMT), FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_DARK));
        pRecDate.setAlignment(Element.ALIGN_CENTER);
        pRecDate.setSpacingBefore(3f);
        receiptCell.addElement(pRecDate);

        headerTable.addCell(receiptCell);
        document.add(headerTable);

        // Linea divisoria
        LineSeparator sep = new LineSeparator(1f, 100f, BORDER_COLOR, Element.ALIGN_CENTER, -2);
        document.add(sep);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 4)));

        // ==========================================
        // 2. DATI DELL'ASSOCIATO / TESSERATO
        // ==========================================
        PdfPTable memberTable = new PdfPTable(1);
        memberTable.setWidthPercentage(100);
        memberTable.setSpacingBefore(8f);
        memberTable.setSpacingAfter(12f);

        PdfPCell memberCell = new PdfPCell();
        memberCell.setBackgroundColor(BG_LIGHT);
        memberCell.setBorderColor(BORDER_COLOR);
        memberCell.setBorderWidth(1f);
        memberCell.setPadding(10f);

        Paragraph pMemberTitle = new Paragraph("DATI DELL'ASSOCIATO / TESSERATO", subTitleFont);
        pMemberTitle.setSpacingAfter(6f);
        memberCell.addElement(pMemberTitle);

        PdfPTable memberInfoTable = new PdfPTable(2);
        memberInfoTable.setWidthPercentage(100);
        memberInfoTable.setWidths(new float[]{1f, 1f});

        PdfPCell c1 = createCleanCell();
        c1.addElement(new Paragraph("Nominativo: " + user.getFullName(), boldFont));
        c1.addElement(new Paragraph("Codice Fiscale: " + (user.getFiscalCode() != null ? user.getFiscalCode() : "—"), normalFont));
        if (user.getBirthDate() != null) {
            String birthInfo = "Nato il: " + user.getBirthDate().format(DATE_FMT);
            if (user.getBirthPlace() != null && !user.getBirthPlace().isBlank()) {
                birthInfo += " a " + user.getBirthPlace() + (user.getBirthProvince() != null ? " (" + user.getBirthProvince() + ")" : "");
            }
            c1.addElement(new Paragraph(birthInfo, normalFont));
        }

        PdfPCell c2 = createCleanCell();
        c2.addElement(new Paragraph("Codice Tesserato: #" + user.getClientCode(), boldAccentFont));
        c2.addElement(new Paragraph("Indirizzo: " + (user.getAddress() != null && !user.getAddress().isBlank() ? user.getAddress() : "—"), normalFont));
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            c2.addElement(new Paragraph("Telefono: " + user.getPhone(), normalFont));
        }

        memberInfoTable.addCell(c1);
        memberInfoTable.addCell(c2);
        memberCell.addElement(memberInfoTable);

        // Se minorenne o con dati genitore presenti
        if (user.isMinor() || (user.getParentName() != null && !user.getParentName().isBlank())) {
            Paragraph pMinor = new Paragraph();
            pMinor.setSpacingBefore(6f);
            pMinor.add(new Chunk("Esercente la potestà genitoriale / Pagatore: ", boldFont));
            pMinor.add(new Chunk((user.getParentName() != null ? user.getParentName() : "—"), normalFont));
            if (user.getParentFiscalCode() != null && !user.getParentFiscalCode().isBlank()) {
                pMinor.add(new Chunk(" (C.F.: " + user.getParentFiscalCode() + ")", normalFont));
            }
            memberCell.addElement(pMinor);
        }

        memberTable.addCell(memberCell);
        document.add(memberTable);

        // ==========================================
        // 3. CAUSALE E DETTAGLIO RICEVUTA
        // ==========================================
        PdfPTable causaleTable = new PdfPTable(1);
        causaleTable.setWidthPercentage(100);
        causaleTable.setSpacingAfter(12f);

        PdfPCell causaleCell = new PdfPCell();
        causaleCell.setBorderColor(BORDER_COLOR);
        causaleCell.setBorderWidth(1f);
        causaleCell.setPadding(10f);

        Paragraph pCausaleTitle = new Paragraph("CAUSALE DEL VERSAMENTO", subTitleFont);
        pCausaleTitle.setSpacingAfter(6f);
        causaleCell.addElement(pCausaleTitle);

        Paragraph pCausaleText = new Paragraph(receipt.getCausale(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_DARK));
        pCausaleText.setLeading(14f);
        causaleCell.addElement(pCausaleText);

        if (receipt.getNotes() != null && !receipt.getNotes().isBlank()) {
            Paragraph pNotes = new Paragraph("Note: " + receipt.getNotes(), smallFont);
            pNotes.setSpacingBefore(4f);
            causaleCell.addElement(pNotes);
        }

        causaleTable.addCell(causaleCell);
        document.add(causaleTable);

        // ==========================================
        // 4. IMPORTO, METODO DI PAGAMENTO E QUIETANZA
        // ==========================================
        PdfPTable amountTable = new PdfPTable(3);
        amountTable.setWidthPercentage(100);
        amountTable.setWidths(new float[]{1.1f, 1f, 0.9f});
        amountTable.setSpacingAfter(12f);

        // Box Modalità
        PdfPCell mCell = new PdfPCell();
        mCell.setBorderColor(BORDER_COLOR);
        mCell.setPadding(10f);
        mCell.addElement(new Paragraph("MODALITÀ DI PAGAMENTO", smallFont));
        String methodLabel = translatePaymentMethod(receipt.getPaymentMethod());
        Paragraph pMethod = new Paragraph(methodLabel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_DARK));
        pMethod.setSpacingBefore(2f);
        mCell.addElement(pMethod);
        amountTable.addCell(mCell);

        // Box Stato Pagamento
        PdfPCell sCell = new PdfPCell();
        sCell.setBackgroundColor(SUCCESS_BG);
        sCell.setBorderColor(new Color(187, 247, 208)); // Green 200
        sCell.setPadding(10f);
        sCell.addElement(new Paragraph("STATO", FontFactory.getFont(FontFactory.HELVETICA, 8, SUCCESS_COLOR)));
        Paragraph pStatus = new Paragraph("PAGATO A SALDO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, SUCCESS_COLOR));
        pStatus.setSpacingBefore(2f);
        sCell.addElement(pStatus);
        amountTable.addCell(sCell);

        // Box Totale Versato
        PdfPCell totCell = new PdfPCell();
        totCell.setBorderColor(BORDER_COLOR);
        totCell.setBackgroundColor(BG_LIGHT);
        totCell.setPadding(10f);
        totCell.addElement(new Paragraph("IMPORTO VERSATO", smallFont));
        NumberFormat currencyFmt = NumberFormat.getCurrencyInstance(IT_LOCALE);
        Paragraph pAmount = new Paragraph(currencyFmt.format(receipt.getAmount()), amountFont);
        pAmount.setSpacingBefore(2f);
        totCell.addElement(pAmount);
        amountTable.addCell(totCell);

        document.add(amountTable);

        // ==========================================
        // 5. SEZIONE MARCA DA BOLLO (SE > 77,47 €)
        // ==========================================
        if (receipt.isStampDutyApplied()) {
            PdfPTable bolloTable = new PdfPTable(2);
            bolloTable.setWidthPercentage(100);
            bolloTable.setWidths(new float[]{1.5f, 0.5f});
            bolloTable.setSpacingAfter(12f);

            PdfPCell bolloTextCell = new PdfPCell();
            bolloTextCell.setBorderColor(BORDER_COLOR);
            bolloTextCell.setPadding(8f);
            Paragraph pBolloH = new Paragraph("IMPOSTA DI BOLLO (€ 2,00)", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, TEXT_DARK));
            Paragraph pBolloDesc = new Paragraph(
                "Operazione con importo superiore a € 77,47. Imposta di bollo di € 2,00 assolta sull'originale ai sensi del D.P.R. 642/1972.",
                smallFont
            );
            pBolloDesc.setSpacingBefore(2f);
            bolloTextCell.addElement(pBolloH);
            bolloTextCell.addElement(pBolloDesc);
            bolloTable.addCell(bolloTextCell);

            PdfPCell bolloBoxCell = new PdfPCell();
            bolloBoxCell.setBorderColor(BORDER_COLOR);
            bolloBoxCell.setBackgroundColor(BG_LIGHT);
            bolloBoxCell.setPadding(8f);
            bolloBoxCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            bolloBoxCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph pBolloStamp = new Paragraph("Marca da bollo\n€ 2,00\nassolta", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, TEXT_MUTED));
            pBolloStamp.setAlignment(Element.ALIGN_CENTER);
            bolloBoxCell.addElement(pBolloStamp);
            bolloTable.addCell(bolloBoxCell);

            document.add(bolloTable);
        }

        // ==========================================
        // 6. DICHIARAZIONE FISCALE ASD
        // ==========================================
        PdfPTable taxNoteTable = new PdfPTable(1);
        taxNoteTable.setWidthPercentage(100);
        taxNoteTable.setSpacingAfter(20f);

        PdfPCell taxCell = new PdfPCell();
        taxCell.setBorderColor(BORDER_COLOR);
        taxCell.setBackgroundColor(BG_LIGHT);
        taxCell.setPadding(8f);

        String customTaxNote = getSettingString(settings, "receiptTaxNote", "");
        if (customTaxNote.isBlank()) {
            customTaxNote = "Operazione istituzionale non soggetta ad IVA ai sensi dell'art. 4, comma 4, D.P.R. 633/1972 e dell'art. 148, comma 3, TUIR (D.P.R. 917/1986). Attività svolta in diretta attuazione degli scopi istituzionali per i propri associati/tesserati.";
        }
        Paragraph pTax = new Paragraph(customTaxNote, smallItalicFont);
        pTax.setLeading(10f);
        taxCell.addElement(pTax);
        taxNoteTable.addCell(taxCell);
        document.add(taxNoteTable);

        // ==========================================
        // 7. FIRME
        // ==========================================
        PdfPTable signTable = new PdfPTable(2);
        signTable.setWidthPercentage(100);
        signTable.setWidths(new float[]{1f, 1f});

        PdfPCell signCell1 = new PdfPCell();
        signCell1.setBorder(Rectangle.NO_BORDER);
        signCell1.setPadding(10f);
        Paragraph pSign1Title = new Paragraph("Firma dell'Associato / Tesserato / Genitore", smallFont);
        Paragraph pLine1 = new Paragraph("\n\n____________________________________", smallFont);
        signCell1.addElement(pSign1Title);
        signCell1.addElement(pLine1);
        signTable.addCell(signCell1);

        PdfPCell signCell2 = new PdfPCell();
        signCell2.setBorder(Rectangle.NO_BORDER);
        signCell2.setPadding(10f);
        Paragraph pSign2Title = new Paragraph("Per l'ASD (Il Presidente / Il Tesoriere)", smallFont);
        pSign2Title.setAlignment(Element.ALIGN_RIGHT);
        Paragraph pLine2 = new Paragraph("\n\n____________________________________", smallFont);
        pLine2.setAlignment(Element.ALIGN_RIGHT);
        signCell2.addElement(pSign2Title);
        signCell2.addElement(pLine2);
        signTable.addCell(signCell2);

        document.add(signTable);

        document.close();
        return baos.toByteArray();
    }

    private PdfPCell createCleanCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2f);
        return cell;
    }

    private String getSettingString(Map<String, Object> settings, String key, String defaultValue) {
        if (settings != null && settings.containsKey(key)) {
            Object val = settings.get(key);
            if (val != null && !val.toString().isBlank()) {
                return val.toString().trim();
            }
        }
        return defaultValue != null ? defaultValue : "";
    }

    private String translatePaymentMethod(com.minegolem.backend.domain.enums.PaymentMethod method) {
        if (method == null) return "Non specificato";
        return switch (method) {
            case CASH -> "Contanti";
            case CARD -> "POS / Carta di Credito / Bancomat";
            case TRANSFER -> "Bonifico Bancario";
            case VOUCHER -> "Voucher / Buono";
        };
    }
}
