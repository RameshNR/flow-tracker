<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="travel" uri="https://example.com/tags/travel" %>
<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Travel Booking</title>
<script src="${pageContext.request.contextPath}/assets/js/travel.js" defer></script>
</head><body><main>
<h1>Travel Package Search</h1>
<form id="searchForm" action="${pageContext.request.contextPath}/search.jsp" method="get">
<label for="destination">Destination</label><input id="destination" name="destination" type="text" placeholder="e.g. Goa">
<label for="budget">Maximum budget (₹)</label><input id="budget" name="budget" type="number" min="0" step="500" value="15000" required>
<label for="days">Maximum days</label><input id="days" name="days" type="number" min="1" value="4" required>
<button type="submit">Search packages</button></form>
<p id="formMessage" role="status" aria-live="polite"></p>
<p>Custom tag example: <travel:status value="Ready to plan your trip" /></p>
</main></body></html>
