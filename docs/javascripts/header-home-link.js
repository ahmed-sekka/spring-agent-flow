/* Make the header site title ("AgentFlow4J") clickable → home,
   mirroring the logo's per-page relative href. */
document.addEventListener("DOMContentLoaded", function () {
  var title = document.querySelector(".md-header__title");
  var logo = document.querySelector(".md-header__button.md-logo");
  if (!title || !logo) return;

  title.style.cursor = "pointer";
  title.addEventListener("click", function (e) {
    // leave genuinely interactive children (search, buttons) alone
    if (e.target.closest("a, button, input, label, .md-search")) return;
    var href = logo.getAttribute("href");
    if (href) window.location.href = href;
  });
});
