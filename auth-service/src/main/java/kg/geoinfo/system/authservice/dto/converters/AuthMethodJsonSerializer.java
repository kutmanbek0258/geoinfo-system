package kg.geoinfo.system.authservice.dto.converters;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.io.IOException;

public class AuthMethodJsonSerializer extends JsonSerializer<ClientAuthenticationMethod> {

    @Override
    public void serialize(ClientAuthenticationMethod value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
        if (value == null) {
            return;
        }
        gen.writeString(value.getValue());
    }
}
