package com.garage.service;

import com.garage.domain.Client;
import com.garage.dto.ClientDtos.ClientRequest;
import com.garage.dto.ClientDtos.ClientResponse;
import com.garage.dto.PageResponse;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.ClientRepository;
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

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> search(String q, Boolean active, int page, int size) {
        return PageResponse.from(clientRepository.findAll(spec(q, active), PageUtil.of(page, size, SORT)),
                ClientService::toResponse);
    }

    /** Same filters as the list, capped, for exports. */
    @Transactional(readOnly = true)
    public List<Client> searchForExport(String q, Boolean active, int maxRows) {
        return clientRepository.findAll(spec(q, active), PageRequest.of(0, maxRows, SORT)).getContent();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        String email = request.email().trim().toLowerCase();
        if (clientRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A client with this e-mail already exists");
        }
        Client client = new Client();
        apply(client, request, email);
        return toResponse(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        Client client = find(id);
        String email = request.email().trim().toLowerCase();
        if (clientRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("A client with this e-mail already exists");
        }
        apply(client, request, email);
        return toResponse(client);
    }

    @Transactional
    public ClientResponse setActive(UUID id, boolean active) {
        Client client = find(id);
        client.setActive(active);
        return toResponse(client);
    }

    Client find(UUID id) {
        return clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client not found"));
    }

    private void apply(Client client, ClientRequest request, String email) {
        client.setLastName(request.lastName().trim());
        client.setFirstName(request.firstName().trim());
        client.setEmail(email);
        client.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
    }

    private Specification<Client> spec(String q, Boolean active) {
        Specification<Client> spec = Specification.where(null);
        if (q != null && !q.isBlank()) {
            String like = PageUtil.likeTerm(q);
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("lastName")), like, '\\'),
                    cb.like(cb.lower(root.get("firstName")), like, '\\'),
                    cb.like(cb.lower(root.get("email")), like, '\\'),
                    cb.like(cb.coalesce(root.<String>get("phone"), ""), like, '\\')));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return spec;
    }

    static ClientResponse toResponse(Client c) {
        return new ClientResponse(c.getId(), c.getLastName(), c.getFirstName(), c.getEmail(), c.getPhone(),
                c.isActive());
    }
}
