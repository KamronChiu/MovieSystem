package com.eduaccess.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.eduaccess.ui.FilmListingView;

@Route("access-denied")
@PageTitle("Access Denied")
public class AccessDeniedView extends Div {

    public AccessDeniedView() {
        setSizeFull();
        getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("background", "linear-gradient(135deg, #020b1d 0%, #0a1628 100%)");

        Div content = new Div();
        content.getStyle()
                .set("text-align", "center")
                .set("padding", "48px");

        Span icon = new Span("⛔");
        icon.getStyle()
                .set("font-size", "80px")
                .set("display", "block")
                .set("margin-bottom", "24px");

        H1 title = new H1("Access Denied");
        title.getStyle()
                .set("color", "#ef4444")
                .set("font-size", "36px")
                .set("font-weight", "900")
                .set("margin-bottom", "16px");

        H2 message = new H2("You do not have permission to access this page.");
        message.getStyle()
                .set("color", "rgba(255,255,255,0.8)")
                .set("font-size", "18px")
                .set("font-weight", "400")
                .set("margin-bottom", "32px");

        RouterLink homeLink = new RouterLink("Go to Films", FilmListingView.class);
        homeLink.getStyle()
                .set("display", "inline-block")
                .set("padding", "12px 32px")
                .set("background", "#0072ce")
                .set("color", "white")
                .set("font-weight", "700")
                .set("border-radius", "8px")
                .set("text-decoration", "none");

        content.add(icon, title, message, homeLink);
        add(content);
    }
}
