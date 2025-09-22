package kg.geoinfo.system.authservice.dto.converters;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.io.IOException;

public class GrantTypeJsonSerializer extends JsonSerializer<AuthorizationGrantType> {

    @Override
    public void serialize(
        AuthorizationGrantType value,
        JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider
    ) throws IOException {
        if (value == null) {
            return;
        }
        jsonGenerator.writeString(value.getValue());
    }
}
