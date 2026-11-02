/* Progressive enhancement: the snippets remain selectable without JavaScript. */
if (navigator.clipboard && window.isSecureContext) {
  document.querySelectorAll("[data-copy]").forEach((button) => {
    button.hidden = false;
    button.addEventListener("click", async () => {
      const status = document.getElementById("copy-status");
      try {
        await navigator.clipboard.writeText(document.getElementById(button.dataset.copy).textContent);
        button.textContent = "Copied";
        status.textContent = "Code copied to clipboard.";
      } catch {
        status.textContent = "Copy unavailable. Select the code and copy it manually.";
      }
    });
  });
}
