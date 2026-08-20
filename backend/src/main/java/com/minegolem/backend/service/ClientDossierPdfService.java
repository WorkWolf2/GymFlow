package com.minegolem.backend.service;

import com.lowagie.text.Document; import com.lowagie.text.DocumentException; import com.lowagie.text.Font; import com.lowagie.text.FontFactory; import com.lowagie.text.PageSize; import com.lowagie.text.Paragraph; import com.lowagie.text.Phrase; import com.lowagie.text.pdf.PdfPCell; import com.lowagie.text.pdf.PdfPTable; import com.lowagie.text.pdf.PdfWriter;
import com.minegolem.backend.domain.entity.*; import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.*; import com.minegolem.backend.security.StaffUserDetails;
import jakarta.servlet.http.HttpServletResponse; import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.awt.Color; import java.io.IOException; import java.time.*; import java.time.format.DateTimeFormatter; import java.util.List; import java.util.UUID;

@Service @RequiredArgsConstructor
public class ClientDossierPdfService {
 private final UserRepository users; private final GymRepository gyms; private final ClientDossierNoteRepository notes; private final ClientDossierProgressRepository progress; private final ClientDossierFieldRepository fields;
 private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
 @Transactional(readOnly=true) public void generate(UUID userId,boolean includeNotes,boolean includeProgress,boolean includeFields,HttpServletResponse response) throws IOException,DocumentException {
  UUID gymId=gymId(); User u=users.findByIdAndGymIdAndDeletedAtIsNull(userId,gymId).orElseThrow(()->ResourceNotFoundException.of("User",userId)); Gym gym=gyms.findById(gymId).orElseThrow();
  response.setContentType("application/pdf");response.setHeader("Content-Disposition","attachment; filename=\"dossier_cliente_"+u.getClientCode()+".pdf\"");
  Font title=FontFactory.getFont(FontFactory.HELVETICA_BOLD,18,new Color(30,30,36)), section=FontFactory.getFont(FontFactory.HELVETICA_BOLD,11,new Color(30,30,36)), text=FontFactory.getFont(FontFactory.HELVETICA,9,new Color(30,30,36)), muted=FontFactory.getFont(FontFactory.HELVETICA,9,new Color(110,110,125));
  Document doc=new Document(PageSize.A4,36,36,52,36); PdfWriter.getInstance(doc,response.getOutputStream());doc.open();
  doc.add(new Paragraph(gym.getName().toUpperCase(),FontFactory.getFont(FontFactory.HELVETICA_BOLD,12,new Color(232,200,74))));doc.add(new Paragraph("DOSSIER CLIENTE",title));doc.add(new Paragraph("Generato il "+LocalDate.now().format(DATE),muted));doc.add(new Paragraph(" "));
  doc.add(new Paragraph(u.getFullName()+"  —  Cliente #"+u.getClientCode(),section));doc.add(new Paragraph("Email: "+dash(u.getEmail())+"     Telefono: "+dash(u.getPhone()),text));doc.add(new Paragraph("Indirizzo: "+dash(u.getAddress()),text));doc.add(new Paragraph(" "));
  if(includeFields){List<ClientDossierField> list=fields.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId); if(!list.isEmpty()){doc.add(new Paragraph("Note aggiuntive",section));for(ClientDossierField f:list)doc.add(new Paragraph(dash(f.getFieldValue()),text));doc.add(new Paragraph(" "));}}
  if(includeNotes){List<ClientDossierNote> list=notes.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId); if(!list.isEmpty()){doc.add(new Paragraph("Note",section));for(ClientDossierNote n:list){doc.add(new Paragraph(n.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))+(n.getAuthor()!=null?" — "+n.getAuthor().getFullName():""),muted));doc.add(new Paragraph(n.getContent(),text));doc.add(new Paragraph(" "));}}}
  if(includeProgress){List<ClientDossierProgress> list=progress.findByUserIdAndDeletedAtIsNullOrderByRecordedDateDescCreatedAtDesc(userId); if(!list.isEmpty()){doc.add(new Paragraph("Storico progressi",section));PdfPTable t=new PdfPTable(5);t.setWidthPercentage(100);for(String h:new String[]{"Data","Peso","Altezza","Massa grassa","Massa muscolare"})cell(t,h,FontFactory.getFont(FontFactory.HELVETICA_BOLD,8,Color.WHITE),new Color(45,45,55));for(ClientDossierProgress p:list){cell(t,p.getRecordedDate().format(DATE),text,Color.WHITE);cell(t,num(p.getWeight()),text,Color.WHITE);cell(t,num(p.getHeight()),text,Color.WHITE);cell(t,num(p.getBodyFatPercentage()),text,Color.WHITE);cell(t,num(p.getMuscleMass()),text,Color.WHITE);}doc.add(t);for(ClientDossierProgress p:list)if(p.getObservations()!=null)doc.add(new Paragraph(p.getRecordedDate().format(DATE)+": "+p.getObservations(),text));}}
  doc.close();
 }
 private void cell(PdfPTable t,String s,Font f,Color bg){PdfPCell c=new PdfPCell(new Phrase(dash(s),f));c.setPadding(5);c.setBackgroundColor(bg);c.setBorderColor(new Color(230,230,230));t.addCell(c);} private String num(Object n){return n==null?"—":n.toString();}private String dash(String s){return s==null||s.isBlank()?"—":s;}private UUID gymId(){return ((StaffUserDetails)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getGymId();}
}
