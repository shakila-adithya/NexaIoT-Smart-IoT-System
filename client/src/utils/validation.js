export const isValidEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

export function validateLogin({ email, password }) {
  const errors = {};
  if (!email) errors.email = "Email is required.";
  else if (!isValidEmail(email)) errors.email = "Enter a valid email address.";
  if (!password) errors.password = "Password is required.";
  return errors;
}

export function validateRegister({ fullName, email, password, confirmPassword, agree }) {
  const errors = {};
  if (!fullName || fullName.trim().length < 2) errors.fullName = "Enter your full name.";
  if (!email) errors.email = "Email is required.";
  else if (!isValidEmail(email)) errors.email = "Enter a valid email address.";
  if (!password) errors.password = "Password is required.";
  else if (password.length < 6) errors.password = "Password must be at least 6 characters.";
  if (confirmPassword !== password) errors.confirmPassword = "Passwords do not match.";
  if (!agree) errors.agree = "You must agree to the Terms and Privacy Policy.";
  return errors;
}

export function validateDevice({ name, deviceId, type, location }) {
  const errors = {};
  if (!name) errors.name = "Device name is required.";
  if (!deviceId) errors.deviceId = "Device ID is required.";
  if (!type) errors.type = "Select a device type.";
  if (!location) errors.location = "Location is required.";
  return errors;
}
