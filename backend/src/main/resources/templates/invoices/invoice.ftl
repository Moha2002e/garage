<#ftl output_format="XML">
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="fr" xml:lang="fr">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>Facture ${inv.number}</title>
<style type="text/css">
@page {
  size: A4;
  margin: 16mm 16mm 26mm 16mm;
  @bottom-center { content: element(footer); }
}
body { font-family: Helvetica, Arial, sans-serif; font-size: 9.5pt; color: #1F2937; margin: 0; padding: 0; }
table { border-collapse: collapse; width: 100%; }
td, th { vertical-align: top; padding: 0; }
.teal { color: #0F766E; }
.muted { color: #64748B; }
.small { font-size: 8.5pt; }
.right { text-align: right; }
.header { border-bottom: 2px solid #0F766E; padding-bottom: 10px; }
.logo { width: 46px; height: 46px; }
.logobox { width: 46px; height: 46px; background-color: #0F766E; color: #FFFFFF; font-size: 24pt; font-weight: bold; text-align: center; line-height: 46px; }
.company { font-size: 14pt; font-weight: bold; color: #0F766E; }
.title { font-size: 26pt; font-weight: bold; color: #0F766E; letter-spacing: 1px; }
.number { font-size: 12pt; font-weight: bold; margin-top: 2px; }
.label { font-size: 7.5pt; font-weight: bold; color: #0F766E; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 3px; }
.value { font-size: 10.5pt; font-weight: bold; }
.box { border: 1px solid #D5E3E2; padding: 8px 10px; margin-bottom: 8px; }
.meta td { padding: 3px 0; border-bottom: 1px solid #E5ECEB; }
.meta .k { color: #64748B; width: 45%; }
.meta .v { text-align: right; font-weight: bold; }
.lines { margin-top: 18px; }
.lines th { background-color: #0F766E; color: #FFFFFF; font-size: 8.5pt; text-align: left; padding: 6px 8px; }
.lines td { padding: 6px 8px; border-bottom: 1px solid #E5ECEB; }
.lines .num { text-align: right; }
.row-even td { background-color: #F1F5F5; }
.totals td { padding: 4px 8px; }
.totals .grand td { background-color: #0F766E; color: #FFFFFF; font-weight: bold; font-size: 11pt; padding: 7px 8px; }
.pay { margin-top: 20px; padding: 10px 12px; border: 1px solid #D5E3E2; page-break-inside: avoid; }
.paid { border: 1px solid #15803D; background-color: #F0FDF4; color: #15803D; font-size: 13pt; font-weight: bold; }
.due { font-size: 12pt; font-weight: bold; margin-bottom: 6px; }
.pay td { padding: 2px 0; }
.pay .k { width: 28%; color: #64748B; }
#footer { position: running(footer); text-align: center; font-size: 8pt; color: #64748B; border-top: 1px solid #D5E3E2; padding-top: 6px; }
</style>
</head>
<body>

<div id="footer">
  <div>${company.name}<#if company.vatNumber?has_content> | TVA ${company.vatNumber}</#if><#if company.email?has_content> | ${company.email}</#if><#if company.phone?has_content> | ${company.phone}</#if></div>
  <div>Facture payable dans les 30 jours à compter de la date d'émission. Montants en euros, TVA comprise dans le total TTC.</div>
</div>

<table class="header">
  <tr>
    <td style="width: 58%;">
      <table>
        <tr>
          <td style="width: 58px;">
<#if logo??>
            <img class="logo" src="${logo}" alt="Logo" />
<#else>
            <div class="logobox">G</div>
</#if>
          </td>
          <td>
            <div class="company">${company.name}</div>
            <div class="small muted">
              <#if company.address?has_content>${company.address}<br /></#if>
              <#if company.postalCity?has_content>${company.postalCity}<#if company.country?has_content>, ${company.country}</#if><br /></#if>
              <#if company.vatNumber?has_content>TVA ${company.vatNumber}<br /></#if>
              <#if company.phone?has_content>${company.phone}</#if><#if company.phone?has_content && company.email?has_content> | </#if><#if company.email?has_content>${company.email}</#if>
            </div>
          </td>
        </tr>
      </table>
    </td>
    <td class="right" style="width: 42%;">
      <div class="title">FACTURE</div>
      <div class="number">${inv.number}</div>
    </td>
  </tr>
</table>

<table style="margin-top: 18px;">
  <tr>
    <td style="width: 54%; padding-right: 14px;">
      <div class="box">
        <div class="label">Client</div>
        <div class="value">${inv.clientName}</div>
      </div>
      <div class="box">
        <div class="label">Véhicule</div>
        <div class="value">${inv.vehicleLabel}</div>
        <div class="muted">Plaque : ${inv.plate}</div>
      </div>
    </td>
    <td style="width: 46%;">
      <table class="meta">
        <tr><td class="k">Date d'émission</td><td class="v">${inv.issueDate}</td></tr>
        <tr><td class="k">Échéance</td><td class="v">${inv.dueDate}</td></tr>
        <tr><td class="k">Statut</td><td class="v"<#if inv.paid> style="color: #15803D;"</#if>>${inv.statusLabel}</td></tr>
        <tr><td class="k">Date du rendez-vous</td><td class="v">${inv.appointmentDate}</td></tr>
      </table>
    </td>
  </tr>
</table>

<table class="lines">
  <thead>
    <tr>
      <th style="width: 50%;">Description</th>
      <th class="num" style="width: 10%; text-align: right;">Quantité</th>
      <th class="num" style="width: 20%; text-align: right;">Prix unitaire</th>
      <th class="num" style="width: 20%; text-align: right;">Total</th>
    </tr>
  </thead>
  <tbody>
<#list lines as l>
    <tr class="row-${l?item_parity}">
      <td>${l.description}</td>
      <td class="num">${l.quantity}</td>
      <td class="num">${l.unitPrice}</td>
      <td class="num">${l.total}</td>
    </tr>
</#list>
  </tbody>
</table>

<table style="margin-top: 14px; page-break-inside: avoid;">
  <tr>
    <td style="width: 52%;"></td>
    <td style="width: 48%;">
      <table class="totals">
        <tr><td>Total HT</td><td class="right">${totals.ht}</td></tr>
        <tr><td>TVA (${totals.vatRate})</td><td class="right">${totals.vat}</td></tr>
        <tr class="grand"><td>Total TTC</td><td class="right">${totals.ttc}</td></tr>
      </table>
    </td>
  </tr>
</table>

<#if inv.paid>
<div class="pay paid">PAYÉE<#if inv.paidDate?has_content> le ${inv.paidDate}</#if></div>
<#else>
<div class="pay">
  <div class="due">À payer avant le ${inv.dueDate}</div>
  <table>
    <#if company.iban?has_content><tr><td class="k">IBAN</td><td>${company.iban}</td></tr></#if>
    <#if company.bic?has_content><tr><td class="k">BIC</td><td>${company.bic}</td></tr></#if>
    <tr><td class="k">Communication</td><td>${inv.number}</td></tr>
  </table>
</div>
</#if>

</body>
</html>
