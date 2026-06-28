# Politica de confidențialitate ScamGuard

**Versiunea 2.0** · În vigoare de la: [data publicării]

---

## 1. Cine suntem

ScamGuard este o aplicație mobilă pentru Android care avertizează utilizatorul cu privire la posibile apeluri telefonice și mesaje SMS frauduloase. Aplicația este dezvoltată de o persoană fizică și este distribuită prin Google Play.

**Persoana de contact pentru chestiuni de confidențialitate:** [email-ul tău]
**Jurisdicție:** Republica Moldova
**Legislația aplicabilă:** Legea Republicii Moldova nr. 133 din 8 iulie 2011 privind protecția datelor cu caracter personal, precum și Regulamentul general al UE privind protecția datelor (GDPR) pentru utilizatorii din statele Uniunii Europene.

---

## 2. Principiul fundamental: totul rămâne local

**Mesajele dumneavoastră SMS, jurnalul apelurilor și contactele sunt prelucrate exclusiv pe dispozitivul dumneavoastră și nu sunt niciodată transmise unor terți, serverelor noastre sau oriunde altundeva.**

Aceasta nu este o formulare de marketing, ci o caracteristică tehnică a aplicației. Analiza mesajelor și apelurilor primite are loc local — nu putem accesa datele dumneavoastră nici măcar tehnic, deoarece acestea nu sunt stocate nicăieri în afara telefonului dumneavoastră.

---

## 3. Ce permisiuni solicită aplicația și pentru ce

Pentru funcționarea aplicației ScamGuard sunt necesare următoarele permisiuni Android. Fiecare permisiune este solicitată separat la prima lansare și puteți refuza oricare dintre ele — însă fără unele dintre ele aplicația nu își va putea îndeplini funcția.

| Permisiune | Pentru ce este folosită | Ce face aplicația |
|---|---|---|
| **Citire SMS** (`READ_SMS`) | Analiza SMS-urilor primite | Scanează local textul mesajelor primite, caută indicii de coduri de la bănci și instituții de credit |
| **Recepție SMS** (`RECEIVE_SMS`) | Reacție la noi SMS-uri în timp real | Se declanșează la primirea unui mesaj nou, pentru a vă avertiza la timp |
| **Starea telefonului** (`READ_PHONE_STATE`) | Determinarea dacă este în desfășurare un apel | Permite înțelegerea contextului: dacă un apel este în desfășurare și simultan a sosit un cod, aceasta este o combinație suspectă |
| **Gestionare apeluri** (`ANSWER_PHONE_CALLS`) | Butonul „Închide apelul" din avertizare | Permite încheierea unui apel suspect cu o singură atingere |
| **Notificări** (`POST_NOTIFICATIONS`) | Afișarea avertizărilor | Fără această permisiune nu veți vedea alerta |
| **Internet** (`INTERNET`) | Descărcarea bazei actualizate de bănci și trimiterea confirmării consimțământului | Vezi secțiunea 4 de mai jos — singurul lucru pe care aplicația îl transmite prin internet |

Aplicația **nu solicită** acces la cameră, microfon, locație, fotografii, fișiere sau contacte.

---

## 4. Ce transmite aplicația prin internet

Pentru a fi complet transparenți, există **două** cereri pe care aplicația le efectuează prin rețea:

### 4.1. Confirmarea consimțământului (o singură dată, la prima lansare)

Atunci când acceptați Politica de confidențialitate și Acordul utilizatorului pe ecranul de bun venit, aplicația transmite pe serverul nostru o înregistrare minimă:

```json
{
  "install_id": "UUID aleatoriu generat pe telefonul dumneavoastră",
  "consent_version": "2.0",
  "consented_at": "2026-06-26T14:23:00Z",
  "locale": "ro-MD",
  "app_version": "2.0.0"
}
```

**install_id** este un identificator aleatoriu pe care dispozitivul dumneavoastră îl generează o singură dată, la prima instalare a aplicației. Acesta nu este asociat cu numele, numărul de telefon, IMEI, e-mail, contul Google sau orice alte date cu caracter personal. Este o etichetă tehnică anonimă, necesară pentru evidența noastră juridică — pentru a putea demonstra că la un anumit moment un anumit dispozitiv a acceptat o anumită versiune a documentelor.

Aceste date sunt stocate pe infrastructura **Cloudflare D1** (bază de date) pe servere situate în Europa. Termenul de păstrare: 7 ani (în conformitate cu cerințele tipice pentru evidența juridică).

La reinstalarea aplicației, ștergerea datelor acesteia din setările Android sau resetarea telefonului, **install_id** se schimbă — iar dumneavoastră veți vedea din nou ecranul de consimțământ.

### 4.2. Actualizarea bazei de date a băncilor (periodic, în fundal)

O dată pe săptămână, aplicația descarcă de pe serverul nostru lista actualizată cu denumirile băncilor, organizațiilor de microfinanțare, operatorilor de telecomunicații și serviciilor publice din Moldova — pentru a identifica corect expeditorii SMS-urilor (de exemplu, pentru a înțelege că expeditorul `MAIB` reprezintă „Banca MAIB").

Aceasta este o **cerere GET anonimă** către un fișier public. Nicio informație despre dumneavoastră nu este transmisă în această cerere, cu excepția adresei IP standard, care este vizibilă pentru orice server la orice cerere prin internet. Nu înregistrăm aceste cereri și nu le asociem cu install_id-ul dumneavoastră.

---

## 5. Ce NU face aplicația

- ❌ Nu transmite SMS-urile dumneavoastră nimănui
- ❌ Nu transmite înregistrări ale apelurilor sau conținutul acestora
- ❌ Nu are acces la contactele dumneavoastră și nu le transmite
- ❌ Nu colectează date despre locația dumneavoastră
- ❌ Nu monitorizează utilizarea altor aplicații
- ❌ Nu folosește identificatori publicitari și nu afișează reclame
- ❌ Nu transmite date către terți, rețele publicitare sau servicii de analiză
- ❌ Nu folosește Google Analytics, Firebase Analytics, Crashlytics sau instrumente similare
- ❌ Nu stochează date cu caracter personal pe serverele noastre

---

## 6. Unde sunt stocate datele dumneavoastră

| Date | Unde sunt stocate | Cine are acces |
|---|---|---|
| SMS, apeluri, contacte | Doar pe telefonul dumneavoastră | Doar dumneavoastră |
| Setările aplicației | Doar pe telefonul dumneavoastră | Doar dumneavoastră |
| Istoricul declanșărilor de avertizări | Doar pe telefonul dumneavoastră | Doar dumneavoastră |
| Înregistrarea consimțământului (install_id + versiune + dată) | Cloudflare D1 (Europa) | Dezvoltatorul aplicației |

---

## 7. Drepturile dumneavoastră

În conformitate cu Legea Republicii Moldova nr. 133/2011 și cu GDPR, dispuneți de următoarele drepturi:

- **Dreptul de a fi informat** cu privire la datele stocate despre dumneavoastră. Deoarece păstrăm doar o înregistrare anonimă a consimțământului asociată cu install_id-ul, vizibil exclusiv în aplicația dumneavoastră (în secțiunea „Despre aplicație" → „Identificator de instalare"), puteți solicita această înregistrare, comunicându-ne install_id-ul dumneavoastră.
- **Dreptul la ștergerea datelor**. Înregistrarea consimțământului poate fi ștearsă la cerere. Trimiteți un mesaj la [email] cu indicarea install_id-ului dumneavoastră și cu solicitarea de ștergere a datelor. Cererea este procesată în termen de 30 de zile.
- **Dreptul de a dezinstala aplicația**. Dezinstalarea aplicației de pe dispozitiv determină ștergerea automată a tuturor datelor locale (setări, istoricul declanșărilor). Înregistrarea consimțământului de pe server rămâne — aceasta trebuie ștearsă prin solicitare separată (vezi mai sus).
- **Dreptul de a depune plângere**. Dacă considerați că drepturile dumneavoastră au fost încălcate, puteți sesiza Centrul Național pentru Protecția Datelor cu Caracter Personal al Republicii Moldova ([cnpdcp.md](https://cnpdcp.md)) sau, în cazul rezidenților UE, autoritatea de supraveghere competentă din statul dumneavoastră.

---

## 8. Securitatea

- Toate cererile prin internet sunt criptate prin HTTPS (TLS 1.3)
- Serverul Cloudflare D1 este situat în Europa și respectă cerințele GDPR
- Accesul la bază de date este limitat la contul dezvoltatorului și este protejat prin autentificare cu doi factori
- Aplicația funcționează în sandbox-ul local Android, izolată de alte aplicații

---

## 9. Minorii

Aplicația **nu este destinată persoanelor cu vârsta sub 18 ani**. Nu colectăm în mod conștient date de la minori. Restricțiile de vârstă sunt detaliate în Acordul utilizatorului.

---

## 10. Modificări ale politicii

În cazul modificărilor substanțiale ale prezentei Politici, vom publica o nouă versiune (de exemplu, v2.1, v3.0). La următoarea deschidere a aplicației, vi se va propune să vă familiarizați cu noua versiune și să confirmați consimțământul. Până în acel moment, rămâne valabilă versiunea pe care ați acceptat-o anterior.

Istoricul versiunilor Politicii este disponibil la adresa: [URL GitHub Pages]/privacy-history

---

## 11. Contact

Pentru toate întrebările legate de prelucrarea datelor, aplicația ScamGuard, retragerea consimțământului sau ștergerea înregistrărilor:

**Email:** [email-ul tău]
**Răspuns:** în termen de 30 de zile calendaristice

---

*Document actualizat: [data]. Versiunea 2.0.*
*Versiunea anterioară (1.1) este disponibilă la [link] pentru utilizatorii care au acceptat-o.*
