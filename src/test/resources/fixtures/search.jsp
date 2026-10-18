<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,com.travel.DestinationService,com.travel.Travel" %>
<%!
private String escapeHtml(String value) {
    if (value == null) return "";
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#x27;");
}
%>
<%
request.setCharacterEncoding("UTF-8");
String destination = request.getParameter("destination");
String budgetValue = request.getParameter("budget");
String daysValue = request.getParameter("days");
List<Travel> packages = java.util.Collections.emptyList();
String error = null;
try {
    double budget = budgetValue == null || budgetValue.isBlank() ? 15000 : Double.parseDouble(budgetValue);
    int days = daysValue == null || daysValue.isBlank() ? 4 : Integer.parseInt(daysValue);
    if (budget < 0 || days <= 0) throw new IllegalArgumentException("Invalid budget or duration");
    packages = new DestinationService().search(destination, budget, days);
} catch (IllegalArgumentException ex) {
    error = "Please enter a valid budget and duration.";
}
%>
<!DOCTYPE html><html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Travel Search Results</title></head><body><main>
<h1>Search Results</h1>
<% if (error != null) { %><p role="alert"><%= error %></p>
<% } else if (packages.isEmpty()) { %><p>No matching packages found.</p>
<% } else { %><table><thead><tr><th>ID</th><th>Destination</th><th>Price</th><th>Days</th><th>Seats</th></tr></thead><tbody>
<% for (Travel travel : packages) { %><tr><td><%= travel.getId() %></td><td><%= escapeHtml(travel.getDestination()) %></td><td>₹<%= String.format(java.util.Locale.ROOT, "%.2f", travel.getPackagePrice()) %></td><td><%= travel.getDurationDays() %></td><td><%= travel.getAvailableSeats() %></td></tr><% } %>
</tbody></table><% } %>
<p><a href="${pageContext.request.contextPath}/index.jsp">Back to search</a></p>
</main></body></html>
