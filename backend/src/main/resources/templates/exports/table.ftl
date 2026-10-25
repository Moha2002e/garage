<#ftl output_format="XML" strip_whitespace=true>
<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
  <Styles>
    <Style ss:ID="header"><Font ss:Bold="1"/><Interior ss:Color="#D9E1F2" ss:Pattern="Solid"/></Style>
  </Styles>
  <Worksheet ss:Name="${sheetName}">
    <Table>
      <#list headers as h><Column ss:AutoFitWidth="1" ss:Width="120"/></#list>
      <Row>
        <#list headers as h><Cell ss:StyleID="header"><Data ss:Type="String">${h}</Data></Cell></#list>
      </Row>
      <#list rows as row>
      <Row>
        <#list row as cell><Cell><Data ss:Type="String">${cell}</Data></Cell></#list>
      </Row>
      </#list>
    </Table>
  </Worksheet>
</Workbook>
