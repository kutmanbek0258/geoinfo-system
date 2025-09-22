package kg.geoinfo.system.authservice.dto.converters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.io.IOException;

public class GrantTypeJsonDeserializer extends JsonDeserializer<AuthorizationGrantType> {

    @Override
    public AuthorizationGrantType deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
        throws IOException {
        String strValue = jsonParser.getText();
        if (StringUtils.isEmpty(strValue)) {
            return null;
        }
        return new AuthorizationGrantType(strValue);
    }
}
