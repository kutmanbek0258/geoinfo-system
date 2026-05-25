package kg.geoinfo.system.geoprintservice.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PdfBuilder {

    private final TemplateEngine templateEngine;

    public byte[] buildPdfReport(BufferedImage mapImage, Map<String, Object> attributes) throws Exception {
        // Конвертация BufferedImage в Base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(mapImage, "png", baos);
        String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());

        Context context = new Context();
        context.setVariable("mapImageBase64", base64Image);
        context.setVariable("attributes", attributes);

        String htmlContent = templateEngine.process("layouts/print_layout", context);

        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(htmlContent, "/");
        builder.toStream(pdfOutputStream);
        builder.run();

        return pdfOutputStream.toByteArray();
    }
}
