package com.serviqo;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

final class InvoicePrinter {
    private InvoicePrinter() {
    }

    static void showPreview(Component parent, ServiceRepository.Invoice invoice) {
        JEditorPane preview = createPreview(invoice);
        JScrollPane scroll = new JScrollPane(preview);
        scroll.setPreferredSize(new Dimension(620, 455));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton print = new JButton("Print invoice");
        print.addActionListener(event -> print(preview));
        actions.add(print);
        panel.add(actions, BorderLayout.SOUTH);

        JOptionPane.showOptionDialog(parent, panel, "Invoice " + invoiceNumber(invoice.job().id()),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new String[]{"Close"}, "Close");
    }

    static JEditorPane createPreview(ServiceRepository.Invoice invoice) {
        JEditorPane preview = new JEditorPane("text/html", buildHtml(invoice));
        preview.setEditable(false);
        preview.setCaretPosition(0);
        return preview;
    }

    static String invoiceNumber(long jobId) {
        return String.format(Locale.ROOT, "SRV-%06d", jobId);
    }

    private static String buildHtml(ServiceRepository.Invoice invoice) {
        ServiceRepository.Job job = invoice.job();
        ServiceRepository.Costs costs = invoice.costs();
        StringBuilder html = new StringBuilder("""
                <html><head><style>
                body { font-family: sans-serif; font-size: 10pt; color: #202530; margin: 14px; }
                h1 { font-size: 21pt; color: #172b4d; margin: 0; }
                h2 { font-size: 12pt; color: #172b4d; margin: 12px 0 4px 0; }
                th { background-color: #e9edf4; }
                </style></head><body>
                <h1>Serviqo</h1><h2>Service invoice</h2>
                """);
        addDetail(html, "Invoice number", invoiceNumber(job.id()));
        addDetail(html, "Invoice date", invoice.date());
        addDetail(html, "Service date", job.serviceDate());
        html.append("<h2>Customer and vehicle</h2>");
        addDetail(html, "Customer", job.owner());
        addDetail(html, "Phone", invoice.customerPhone());
        addDetail(html, "Registration", job.registration());
        addDetail(html, "Vehicle", job.vehicle());
        html.append("<h2>Service details</h2>");
        addDetail(html, "Reported problem", job.problem());
        addDetail(html, "Repair description", job.description());
        html.append("<h2>Parts and labor</h2>");
        html.append("""
                <table width="100%" border="0" cellspacing="0" cellpadding="5">
                <tr><th align="left">Spare part</th><th align="right">Qty</th>
                <th align="right">Unit price</th><th align="right">Line total</th></tr>
                """);
        for (ServiceRepository.Part part : costs.parts()) {
            ServiceRepository.PartDetails details = part.details();
            html.append("<tr><td>").append(escape(details.name())).append("</td>");
            html.append("<td align='right'>").append(details.quantity()).append("</td>");
            html.append("<td align='right'>").append(ServicePanel.formatMoney(BigDecimal.valueOf(details.unitPriceCents(), 2)))
                    .append("</td>");
            html.append("<td align='right'>").append(ServicePanel.formatMoney(details.total())).append("</td></tr>");
        }
        if (costs.parts().isEmpty()) {
            html.append("<tr><td colspan='4'>No spare parts recorded.</td></tr>");
        }
        html.append("</table><hr>");
        addTotal(html, "Parts subtotal", costs.partsTotal());
        addTotal(html, "Labor charge", BigDecimal.valueOf(costs.laborCents(), 2));
        addTotal(html, "Grand total (LKR)", costs.grandTotal());
        return html.append("</body></html>").toString();
    }

    private static void addDetail(StringBuilder html, String label, String value) {
        String text = value == null || value.isBlank() ? "Not recorded" : escape(value);
        html.append("<div><b>").append(label).append(":</b> ").append(text).append("</div>");
    }

    private static void addTotal(StringBuilder html, String label, BigDecimal amount) {
        html.append("<div align='right'><b>").append(label).append(":</b> ")
                .append(ServicePanel.formatMoney(amount)).append("</div>");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;")
                .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
    }

    private static void print(JEditorPane preview) {
        try {
            preview.print(null, null, true, null, null, true);
        } catch (PrinterException exception) {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = "Check your printer and try again.";
            }
            JOptionPane.showMessageDialog(preview, "Could not print the invoice. " + message,
                    "Print invoice", JOptionPane.ERROR_MESSAGE);
        }
    }
}
