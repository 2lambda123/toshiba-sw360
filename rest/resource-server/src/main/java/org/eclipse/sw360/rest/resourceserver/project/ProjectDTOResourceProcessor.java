package org.eclipse.sw360.rest.resourceserver.project;

import lombok.RequiredArgsConstructor;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectDTO;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@Component
@RequiredArgsConstructor
public class ProjectDTOResourceProcessor implements RepresentationModelProcessor<EntityModel<ProjectDTO>> {

    @Override
    public EntityModel<ProjectDTO> process(EntityModel<ProjectDTO> model) {
        ProjectDTO project = model.getContent();
        Link selfLink = linkTo(ProjectController.class)
                .slash("api" + ProjectController.PROJECTS_URL + "/dependency/" + project.getId()).withSelfRel();
        model.add(selfLink);
        return model;
    }
}