package kg.geoinfo.system.geoprintservice.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfBuilder {

    private final TemplateEngine templateEngine;

    public byte[] buildPdfReport(BufferedImage mapImage, Map<String, Object> attributes, String layout) throws Exception {
        // Конвертация BufferedImage в Base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(mapImage, "png", baos);
        String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());

        String pageSize = layout != null ? layout.split("_")[0].toLowerCase() : "a4";
        String orientation = layout != null && layout.toUpperCase().contains("PORTRAIT") ? "" : "landscape";

        Context context = new Context();
        context.setVariable("mapImageBase64", base64Image);
        context.setVariable("attributes", attributes);
        context.setVariable("pageSize", pageSize);
        context.setVariable("orientation", orientation);

        String htmlContent = templateEngine.process("layouts/print_layout", context);

        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        
        // Регистрация шрифта из ресурсов для поддержки кириллицы
        builder.useFont(() -> getClass().getResourceAsStream("/fonts/DejaVuSans.ttf"), "DejaVu Sans");

        builder.withHtmlContent(htmlContent, "/");
        builder.toStream(pdfOutputStream);
        builder.run();

        return pdfOutputStream.toByteArray();
    }
}
