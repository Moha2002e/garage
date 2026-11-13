package com.garage.service;

import com.garage.domain.Client;
import com.garage.dto.ClientDtos.ClientRequest;
import com.garage.dto.ClientDtos.ClientResponse;
import com.garage.dto.PageResponse;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.mapper.ClientMapper;
import com.garage.mapper.MapperSupport;
import com.garage.repository.ClientRepository;
import com.garage.specification.ClientSpecifications;
import com.garage.util.PageUtil;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    private static final Sort SORT = Sort.by("lastName", "firstName");

    private final ClientRepository clientRepository;
    private final ClientMapper mapper;

    public ClientService(ClientRepository clientRepository, ClientMapper mapper) {
        this.clientRepository = clientRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> search(String q, Boolean active, int page, int size) {
        return PageResponse.from(clientRepository.findAll(spec(q, active), PageUtil.of(page, size, SORT)),
                mapper::toResponse);
    }

    /** Same filters as the list, capped, for exports. */
    @Transactional(readOnly = true)
    public List<Client> searchForExport(String q, Boolean active, int maxRows) {
        return clientRepository.findAll(spec(q, active), PageRequest.of(0, maxRows, SORT)).getContent();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        if (clientRepository.existsByEmailIgnoreCase(MapperSupport.normalizeEmail(request.email()))) {
            throw new ConflictException("A client with this e-mail already exists");
        }
        return mapper.toResponse(clientRepository.save(mapper.toEntity(request)));
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        Client client = find(id);
        if (clientRepository.existsByEmailIgnoreCaseAndIdNot(MapperSupport.normalizeEmail(request.email()), id)) {
            throw new ConflictException("A client with this e-mail already exists");
        }
        mapper.update(request, client);
        return mapper.toResponse(client);
    }

    @Transactional
    public ClientResponse setActive(UUID id, boolean active) {
        Client client = find(id);
        client.setActive(active);
        return mapper.toResponse(client);
    }

    Client find(UUID id) {
        return clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client not found"));
    }

    private Specification<Client> spec(String q, Boolean active) {
        return Specification.where(ClientSpecifications.matchesText(q))
                .and(ClientSpecifications.hasActive(active));
    }
}
