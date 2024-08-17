let message = {type: "captcha", url: document.location.href, innerHTML: document.body.parentElement.innerHTML};
browser.runtime.sendNativeMessage("browser", message);