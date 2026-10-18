"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("searchForm");
    const message = document.getElementById("formMessage");
    if (!form) return;

    form.addEventListener("submit", (event) => {
        const budget = Number(form.elements.budget.value);
        const days = Number(form.elements.days.value);
        newFun(budget, days, event, message);
    });
    consoleFun();
});


function newFun(budget, days, event, message) {
    if (!Number.isFinite(budget) || budget < 0 || !Number.isInteger(days) || days < 1) {
        event.preventDefault();
        if (message) message.textContent = "Enter a non-negative budget and a duration of at least one day.";
    } else if (message) {
        message.textContent = "Searching available travel packages...";
    }
}

function consoleFun() {
    console.log("Hai");
}