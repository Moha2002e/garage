<#ftl output_format="XML" strip_whitespace=true>
<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
  <Styles>
    <Style ss:ID="title"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Size="16" ss:Color="#8F6A1F"/></Style>
    <Style ss:ID="subtitle"><Font ss:Color="#64748B"/></Style>
    <Style ss:ID="header"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#D9A93F"/><Interior ss:Color="#171614" ss:Pattern="Solid"/></Style>
    <Style ss:ID="text"><Alignment ss:Vertical="Center"/><Font/></Style>
    <Style ss:ID="text_alt"><Alignment ss:Vertical="Center"/><Font/><Interior ss:Color="#F1F5F5" ss:Pattern="Solid"/></Style>
    <Style ss:ID="ok"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#15803D"/></Style>
    <Style ss:ID="ok_alt"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#15803D"/><Interior ss:Color="#F1F5F5" ss:Pattern="Solid"/></Style>
    <Style ss:ID="ko"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#B91C1C"/></Style>
    <Style ss:ID="ko_alt"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#B91C1C"/><Interior ss:Color="#F1F5F5" ss:Pattern="Solid"/></Style>
    <Style ss:ID="info"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#1D4ED8"/></Style>
    <Style ss:ID="info_alt"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#1D4ED8"/><Interior ss:Color="#F1F5F5" ss:Pattern="Solid"/></Style>
    <Style ss:ID="warn"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#C2410C"/></Style>
    <Style ss:ID="warn_alt"><Alignment ss:Vertical="Center"/><Font ss:Bold="1" ss:Color="#C2410C"/><Interior ss:Color="#F1F5F5" ss:Pattern="Solid"/></Style>
  </Styles>
  <Worksheet ss:Name="${sheetName}">
    <Table>
      <#list widths as w><Column ss:Width="${(w * 5.5)?c}"/></#list>
      <Row ss:Height="26"><Cell ss:MergeAcross="${(headers?size - 1)?c}" ss:StyleID="title"><Data ss:Type="String">${title}</Data></Cell></Row>
      <Row><Cell ss:MergeAcross="${(headers?size - 1)?c}" ss:StyleID="subtitle"><Data ss:Type="String">${sheetName}</Data></Cell></Row>
      <Row ss:Height="20">
        <#list headers as h><Cell ss:StyleID="header"><Data ss:Type="String">${h}</Data></Cell></#list>
      </Row>
      <#list rows as row>
      <Row>
        <#list row as cell><Cell ss:StyleID="${cell.s}"><Data ss:Type="String">${cell.v}</Data></Cell></#list>
      </Row>
      </#list>
    </Table>
  </Worksheet>
</Workbook>
