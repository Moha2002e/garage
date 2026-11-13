package com.garage.mapper;

import com.garage.domain.Part;
import com.garage.domain.Quote;
import com.garage.domain.QuoteLine;
import com.garage.dto.QuoteDtos.QuoteLineRequest;
import com.garage.dto.QuoteDtos.QuoteLineResponse;
import com.garage.dto.QuoteDtos.QuoteResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(uses = MapperSupport.class)
public interface QuoteMapper {

    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "lines", source = "lines", qualifiedByName = "activeLines")
    QuoteResponse toResponse(Quote quote);

    @Mapping(target = "partId", source = "part.id")
    @Mapping(target = "partReference", source = "part.reference")
    @Mapping(target = "lineTotal", expression = "java(line.lineTotal())")
    QuoteLineResponse toLineResponse(QuoteLine line);

    /** Replaced lines stay in the table with active = false and are never returned. */
    @Named("activeLines")
    default List<QuoteLineResponse> activeLines(List<QuoteLine> lines) {
        return lines.stream().filter(QuoteLine::isActive).map(this::toLineResponse).toList();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "quote", source = "quote")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "description", source = "request.description", qualifiedByName = "trim")
    @Mapping(target = "quantity", source = "request.quantity")
    @Mapping(target = "unitPrice", source = "request.unitPrice")
    @Mapping(target = "part", source = "part")
    QuoteLine toLine(QuoteLineRequest request, Quote quote, int sortOrder, Part part);
}
