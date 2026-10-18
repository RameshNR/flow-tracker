"use strict";

// Fixture for the JavaScript call tests: functions that call each other.
function validate(form) {
    const ok = checkBudget(form.budget.value) && checkDays(form.days.value);
    if (!ok) showError("Please check the budget and days.");
    return ok;
}

function checkBudget(value) {
    return Number(value) >= 0; // "checkDays(1)" in a string or comment is not a call
}

const checkDays = (value) => {
    return Number.isInteger(Number(value)) && Number(value) > 0;
};

function showError(text) {
    document.getElementById("formMessage").textContent = text;
}

document.addEventListener("submit", (event) => {
    if (!validate(event.target)) event.preventDefault();
});
