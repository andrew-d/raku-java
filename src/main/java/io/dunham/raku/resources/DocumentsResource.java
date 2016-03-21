package io.dunham.raku.resources;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import io.dropwizard.jersey.params.LongParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dunham.raku.db.DocumentDAO;
import io.dunham.raku.db.TagDAO;
import io.dunham.raku.helpers.pagination.PaginationHelpers;
import io.dunham.raku.helpers.pagination.PaginationParams;
import io.dunham.raku.model.Document;
import io.dunham.raku.viewmodel.DocumentVM;
import io.dunham.raku.viewmodel.TopLevelVM;


@Path("/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
public class DocumentsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentsResource.class);
    private static final LongParam DEFAULT_PAGE = new LongParam("1");
    private static final LongParam DEFAULT_PER_PAGE = new LongParam("20");

    private final DocumentDAO documentDAO;
    private final TagDAO tagDAO;

    @Inject
    public DocumentsResource(DocumentDAO docDAO, TagDAO tagDAO) {
        this.documentDAO = docDAO;
        this.tagDAO = tagDAO;
    }

    @Timed
    @POST
    public DocumentVM createDocument(@Valid Document document) {
        final long id = documentDAO.save(document);
        document.setId(id);
        return DocumentVM.of(document);
    }

    @Timed
    @GET
    public TopLevelVM listDocuments(
        @Context PaginationParams pagination,
        @Context ContainerRequestContext ctx
    ) {
        final List<Document> documents = documentDAO.findAll(pagination);

        final TopLevelVM ret = TopLevelVM.of(DocumentVM.mapList(documents));

        final long count = documentDAO.count();
        ret.getMeta().setCount(count);
        pagination.setTotal(count);

        // Save pagination in request context so response filter can use it.
        PaginationHelpers.setParams(ctx, pagination);

        return ret;
    }

    private long ensurePositive(long input) {
        if (input < 0) {
            throw new BadRequestException("Value must be positive");
        }
        return input;
    }
}
