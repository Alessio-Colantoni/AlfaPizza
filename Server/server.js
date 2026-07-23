const express = require("express");
const mongoose = require("mongoose");
const bcrypt = require("bcryptjs");
const config = require("./config");
const {
  canonicalCollectionName,
  createSessionToken,
  extractBearerToken,
  hashSessionToken,
  safeSecretEquals
} = require("./security");

process.on("uncaughtException", err => console.error("FATAL:", err));
process.on("unhandledRejection", err => console.error("REJECTION:", err));

const app = express();
app.use(express.json({ limit: "5mb" }));
const PORT = config.PORT;
const APP_TIME_ZONE = process.env.APP_TIME_ZONE || "Europe/Rome";
const SESSION_TTL_HOURS = Math.max(1, Number(process.env.SESSION_TTL_HOURS) || 168);
const CRON_SECRET = process.env.CRON_SECRET || "";

const genericSchema = new mongoose.Schema({}, { strict: false, timestamps: true });
const normalizeCollectionName = (name) => name.replace(/\.json$/i, "");
const getRomeDateParts = (date = new Date()) => Object.fromEntries(
    new Intl.DateTimeFormat("en-GB", {
        timeZone: APP_TIME_ZONE,
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false
    }).formatToParts(date).map(part => [part.type, part.value])
);
const getServerTime = () => {
    const parts = getRomeDateParts();
    return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
};
const getTodayIndex = () => {
    const weekday = new Intl.DateTimeFormat("en-US", { timeZone: APP_TIME_ZONE, weekday: "short" }).format(new Date());
    const map = { Mon: 0, Tue: 1, Wed: 2, Thu: 3, Fri: 4, Sat: 5, Sun: 6 };
    return map[weekday] ?? 0;
};

const getCurrentWeekKey = () => {
    const parts = getRomeDateParts();
    const localDate = new Date(Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day)));
    localDate.setUTCDate(localDate.getUTCDate() - getTodayIndex());
    return localDate.toISOString().slice(0, 10);
};

const getModel = (collectionName) => {
    const normalized = normalizeCollectionName(collectionName);
    return mongoose.models[normalized] || mongoose.model(normalized, genericSchema, normalized);
};

const applySession = (query, session) => session ? query.session(session) : query;
const sessionOptions = (session, options = {}) => session ? { ...options, session } : options;

async function createAuthSession(user) {
  const token = createSessionToken();
  const expiresAt = new Date(Date.now() + SESSION_TTL_HOURS * 60 * 60 * 1000);
  const SessionModel = getModel("authSessions");
  await SessionModel.deleteMany({ expiresAt: { $lte: new Date() } });
  await SessionModel.create({
    tokenHash: hashSessionToken(token),
    userCode: Number(user.code),
    expiresAt,
    createdAt: new Date()
  });
  return token;
}

async function revokeUserSessions(userCode) {
  await getModel("authSessions").deleteMany({ userCode: Number(userCode) });
}

const getConstraintRank = (priority) => {
    const normalizedPriority = Number(priority);
    if (!normalizedPriority) return 1;
    if (normalizedPriority === 1) return 3;
    return 2;
};

const getConstraintColor = (priority) => {
    const normalizedPriority = Number(priority);
    if (normalizedPriority === 1) return "red";
    if (normalizedPriority) return "yellow";
    return "transparent";
};

const defaultWeekStructure = (isNext) => ({
    isNext,
    listShift: [0, 0, 0, 0, 0, 0, 0],
    maxRider: 7,
    minRider: 1,
    lastDayConstraint: 3
});

const normalizeWeekStructure = (structure, isNext) => ({
    ...defaultWeekStructure(isNext),
    ...(structure || {}),
    isNext
});

async function getWeekStructureForCalendar(targetIsNext, session = null) {
    const WeekStructureModel = getModel("weekStructure");
    const fallback = await applySession(WeekStructureModel.findOne({ isNext: { $exists: false } }).lean(), session);
    const existing = await applySession(WeekStructureModel.findOne({ isNext: targetIsNext }).lean(), session);
    const normalized = normalizeWeekStructure(existing || fallback, targetIsNext);
    delete normalized._id;
    await WeekStructureModel.updateOne(
        { isNext: targetIsNext },
        { $set: normalized },
        sessionOptions(session, { upsert: true })
    );
    return normalized;
}

async function getWeekStructures() {
    const current = await getWeekStructureForCalendar(false);
    const next = await getWeekStructureForCalendar(true);
    if (current.lastDayConstraint !== next.lastDayConstraint) {
        current.lastDayConstraint = next.lastDayConstraint;
        await getModel("weekStructure").updateOne(
            { isNext: false },
            { $set: { lastDayConstraint: next.lastDayConstraint } }
        );
    }
    return [current, next];
}

mongoose.connect(config.MONGO_URI)
  .then(async () => {
    console.log("🍃 MongoDB Atlas Online");
    await getModel("authSessions").collection.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });
    await getModel("automationState").collection.createIndex({ key: 1 }, { unique: true });
  })
  .catch(err => console.error("MongoDB Connection Error:", err));

const authenticate = async (req, res, next) => {
  try {
    if (req.path === "/api/login" || req.path === "/" || req.path === "/api/health") return next();

    if (req.path.startsWith("/api/cron/")) {
      if (!CRON_SECRET) return res.status(503).json({ error: "CRON_SECRET non configurato" });
      if (!safeSecretEquals(CRON_SECRET, req.headers["x-cron-secret"])) {
        return res.status(401).json({ error: "Cron non autorizzato" });
      }
      return next();
    }

    const token = extractBearerToken(req.headers.authorization);
    if (!token) return res.status(401).json({ error: "Effettua il login" });

    const authSession = await getModel("authSessions").findOne({
      tokenHash: hashSessionToken(token),
      expiresAt: { $gt: new Date() }
    }).lean();
    if (!authSession) return res.status(401).json({ error: "Sessione non valida o scaduta" });

    const user = await getModel("users").findOne({ code: Number(authSession.userCode) }).lean();
    if (!user) {
      await getModel("authSessions").deleteOne({ _id: authSession._id });
      return res.status(401).json({ error: "Sessione non valida" });
    }

    req.authUser = user;
    req.authSession = authSession;
    next();
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

app.use(authenticate);

const healthHandler = (req, res) => {
  const databaseOnline = mongoose.connection.readyState === 1;
  res.status(databaseOnline ? 200 : 503).json({
    status: databaseOnline ? "online" : "degraded",
    database: databaseOnline ? "connected" : "disconnected",
    system: "AlfaPizza"
  });
};

app.get("/", healthHandler);
app.get("/api/health", healthHandler);

app.post("/api/login", async (req, res) => {
  try {
    const { email, password } = req.body;
    if (typeof email !== "string" || typeof password !== "string" || !email.trim() || !password) {
      return res.status(400).json({ success: false, message: "Credenziali non valide" });
    }
    const cleanEmail = email.toLowerCase().trim();

    const UserModel = getModel("users");
    const user = await UserModel.findOne({ email: cleanEmail }).lean();

    if (user) {
      const isMatch = await bcrypt.compare(password, user.password);

      if (isMatch) {
        const now = getServerTime();
        await UserModel.updateOne({ _id: user._id }, { $set: { lastAccess: now } });
        user.lastAccess = now;
        delete user.password;
        const token = await createAuthSession(user);
        return res.json({ success: true, user: user, token });
      }
    }
    res.status(401).json({ success: false, message: "Email o password errata" });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
});

app.post("/api/user/update-profile", async (req, res) => {
    try {
        const userCode = Number(req.authUser.code);
        const updateData = {};
        for (const field of ["email", "phone", "password"]) {
            if (Object.prototype.hasOwnProperty.call(req.body || {}, field)) updateData[field] = req.body[field];
        }
        if (Object.keys(updateData).length === 0) {
            return res.status(400).json({ error: "Nessun dato aggiornabile" });
        }

        const UserModel = getModel("users");
        if (updateData.email) {
            if (typeof updateData.email !== "string") return res.status(400).json({ error: "Email non valida" });
            const normalizedEmail = updateData.email.toLowerCase().trim();
            const existing = await UserModel.findOne({ email: normalizedEmail }).lean();
            if (existing && Number(existing.code) !== userCode) {
                return res.status(409).json({ error: "Email gia' in uso" });
            }
            updateData.email = normalizedEmail;
        }

        if (Object.prototype.hasOwnProperty.call(updateData, "password")) {
            if (typeof updateData.password !== "string" || !updateData.password) {
                return res.status(400).json({ error: "Password non valida" });
            }
            updateData.password = await bcrypt.hash(updateData.password, 10);
        }

        const result = await UserModel.findOneAndUpdate(
            { code: userCode },
            { $set: updateData },
            { new: true }
        ).lean();

        if (result) {
            if (updateData.password) await revokeUserSessions(userCode);
            delete result.password;
            res.json({ success: true, user: result, reauthenticate: Boolean(updateData.password) });
        } else {
            res.status(404).json({ error: "Utente non trovato" });
        }
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

app.post("/api/logout", async (req, res) => {
  try {
    await getModel("authSessions").deleteOne({ _id: req.authSession._id });
    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
});

let isGenerating = false;
async function generateOptimalCalendar(targetIsNext = true, session = null) {
  if (isGenerating) return { success: false, error: "Generazione già in corso" };
  isGenerating = true;
  try {
    const weekStructure = await getWeekStructureForCalendar(targetIsNext, session);
    const riders = await applySession(getModel("users").find({ isAdmin: false }).lean(), session);
    const constraintQuery = targetIsNext
      ? { $or: [{ isNext: true }, { isNext: { $exists: false } }] }
      : { isNext: false };
    const constraints = await applySession(getModel("constraints").find(constraintQuery).lean(), session);
    return await finalizeGeneration(targetIsNext, weekStructure, riders, constraints, session);

  } catch (error) {
      return { success: false, error: error.message };
  }
  finally { isGenerating = false; }
}

async function finalizeGeneration(targetIsNext, weekStructure, riders, constraints, session = null) {
    const minRider = Number(weekStructure.minRider) || 0;
    const maxRider = Number(weekStructure.maxRider) || 7;
    const shiftCounts = {};
    riders.forEach(r => shiftCounts[r.code] = 0);
    const newDays = [0, 1, 2, 3, 4, 5, 6].map(d => ({ day: d, listShift: [] }));
    const anomalies = [];

    if (minRider > maxRider) anomalies.push("@CONFIG:min_greater_than_max");

    const getConstraintFor = (riderCode, day) =>
        constraints.find(c => c.riderCode == riderCode && c.day == day);

    for (let day = 0; day < 7; day++) {
      const needed = (weekStructure.listShift && weekStructure.listShift[day]) || 0;
      const assignedToday = new Set();

      // Ogni slot viene assegnato ricalcolando i candidati: maxRider e un solo turno
      // per rider nello stesso giorno sono vincoli duri; le preferenze incidono solo dopo.
      for (let i = 0; i < needed; i++) {
        const eligible = riders
          .filter(r =>
            !assignedToday.has(r.code) &&
            shiftCounts[r.code] < maxRider &&
            Number(getConstraintFor(r.code, day)?.priority) !== 1
          )
          .sort((a, b) => {
            const aUnderMin = shiftCounts[a.code] < minRider ? 0 : 1;
            const bUnderMin = shiftCounts[b.code] < minRider ? 0 : 1;
            if (aUnderMin !== bUnderMin) return aUnderMin - bUnderMin;

            const rankA = getConstraintRank(getConstraintFor(a.code, day)?.priority);
            const rankB = getConstraintRank(getConstraintFor(b.code, day)?.priority);
            if (rankA !== rankB) return rankA - rankB;

            const countDiff = shiftCounts[a.code] - shiftCounts[b.code];
            return countDiff !== 0 ? countDiff : Number(a.code) - Number(b.code);
          });

        const sel = eligible[0];
        if (!sel) {
          newDays[day].listShift.push({ code: -99, color: "red" });
          anomalies.push(`@DAY_${day}:uncovered_shift`);
          continue;
        }

        const constraint = getConstraintFor(sel.code, day);
        const color = constraint ? getConstraintColor(constraint.priority) : "transparent";

        newDays[day].listShift.push({ code: sel.code, color });
        assignedToday.add(sel.code);
        shiftCounts[sel.code]++;
      }
    }

    riders.forEach(r => {
      if (shiftCounts[r.code] < minRider) {
        anomalies.push(`@RIDER_${r.code}:below_min_shifts`);
      }
    });

    await getModel("calendars").updateOne(
        { isNext: targetIsNext },
        { $set: {
            lastUpdate: getServerTime(),
            days: newDays,
            publicationDay: weekStructure.lastDayConstraint || 0,
            anomalies: anomalies.join("\n")
        } },
        sessionOptions(session, { upsert: true })
    );
    return { success: true };
}

async function calculateShiftColor(riderCode, day, targetIsNext = true) {
  if (riderCode == -99) return "red";
  if (riderCode == -1) return "transparent";

  // Cerchiamo il vincolo provando sia formato numero che stringa per massima compatibilita'
  const constraint = await getModel("constraints").findOne({
      $and: [
        { $or: targetIsNext
          ? [{ isNext: true }, { isNext: { $exists: false } }]
          : [{ isNext: false }]
        },
        {
      $or: [
          { riderCode: parseInt(riderCode), day: parseInt(day) },
          { riderCode: riderCode.toString(), day: parseInt(day) },
          { riderCode: parseInt(riderCode), day: day.toString() }
      ]
        }
      ]
  }).lean();

  if (!constraint) return "transparent";
  return getConstraintColor(constraint.priority);
}

async function calculateCalendarAnomalies(calendar, session = null) {
  const weekStructure = await getWeekStructureForCalendar(calendar.isNext === true, session);
  const riders = await applySession(getModel("users").find({ isAdmin: false }).lean(), session);
  const minRider = Number(weekStructure.minRider) || 0;
  const maxRider = Number(weekStructure.maxRider) || 0;
  const riderCodes = new Set(riders.map(rider => Number(rider.code)));
  const shiftCounts = Object.fromEntries(riders.map(rider => [Number(rider.code), 0]));
  const anomalies = new Set();

  if (minRider > maxRider) anomalies.add("@CONFIG:min_greater_than_max");

  for (const workday of calendar.days || []) {
    for (const shift of workday.listShift || []) {
      const code = Number(shift.code);
      if (code === -99 || (code >= 0 && !riderCodes.has(code))) {
        anomalies.add(`@DAY_${workday.day}:uncovered_shift`);
      } else if (riderCodes.has(code)) {
        shiftCounts[code] = (shiftCounts[code] || 0) + 1;
      }
    }
  }

  for (const rider of riders) {
    if ((shiftCounts[Number(rider.code)] || 0) < minRider) {
      anomalies.add(`@RIDER_${rider.code}:below_min_shifts`);
    }
  }

  return [...anomalies].join("\n");
}

function findShiftForRider(calendar, dayNumber, riderCode) {
  const day = (calendar.days || []).find(d => Number(d.day) === Number(dayNumber));
  if (!day) return null;
  const shift = (day.listShift || []).find(s => Number(s.code) === Number(riderCode));
  return shift ? { day, shift } : null;
}

function isCalendarPublishedForRiders(calendar) {
  if (!calendar || calendar.isNext !== true) return true;
  const publicationDay = Number(calendar.publicationDay);
  return Number.isInteger(publicationDay) && publicationDay >= 0 && publicationDay <= 6 && getTodayIndex() >= publicationDay;
}

function validateSwapCalendarState(calendar, swap, acceptedRider = null) {
  const requester = Number(swap.fromRider);
  const fromDay = Number(swap.fromDay);
  const toDay = Number(swap.toDay);

  if (!findShiftForRider(calendar, fromDay, requester)) {
    return "Il rider non ha il turno da scambiare";
  }
  if (findShiftForRider(calendar, toDay, requester)) {
    return "Il rider richiedente lavora gia' nel giorno desiderato";
  }

  if (acceptedRider !== null) {
    const accepted = Number(acceptedRider);
    if (accepted === requester) return "Lo swap richiede due rider diversi";
    if (!findShiftForRider(calendar, toDay, accepted)) {
      return "Il rider non ha il turno richiesto";
    }
    if (findShiftForRider(calendar, fromDay, accepted)) {
      return "Il rider lavora gia' nel giorno proposto";
    }
  }

  return null;
}

async function replaceDeletedRiderInCalendars(riderCode) {
  const normalizedCode = Number(riderCode);
  const CalendarModel = getModel("calendars");
  const calendars = await CalendarModel.find({
    "days.listShift.code": { $in: [normalizedCode, normalizedCode.toString()] }
  }).lean();
  const now = getServerTime();

  for (const calendar of calendars) {
    const anomalies = new Set(
      (calendar.anomalies || "")
        .split("\n")
        .filter(item => item && item !== `@RIDER_${normalizedCode}:below_min_shifts`)
    );
    let changed = false;

    for (const workday of calendar.days || []) {
      let dayAffected = false;
      for (const shift of workday.listShift || []) {
        if (Number(shift.code) === normalizedCode) {
          shift.code = -99;
          shift.color = "red";
          changed = true;
          dayAffected = true;
        }
      }
      if (dayAffected) anomalies.add(`@DAY_${workday.day}:uncovered_shift`);
    }

    if (changed) {
      await CalendarModel.updateOne(
        { _id: calendar._id },
        { $set: { days: calendar.days, anomalies: [...anomalies].join("\n"), lastUpdate: now } }
      );
    }
  }
}

async function applyApprovedSwap(swap) {
  const CalendarModel = getModel("calendars");
  const targetIsNext = swap.isNext === true;
  const calendar = await CalendarModel.findOne({ isNext: targetIsNext }).lean();
  if (!calendar) return { success: false, status: 404, error: "Calendario non trovato" };
  if (targetIsNext && !isCalendarPublishedForRiders(calendar)) {
    return { success: false, status: 403, error: "Il calendario della prossima settimana non e' ancora pubblicato" };
  }

  const validationError = validateSwapCalendarState(calendar, swap, swap.firstRiderAccepted);
  if (validationError) return { success: false, status: 409, error: validationError };

  const fromShift = findShiftForRider(calendar, swap.fromDay, swap.fromRider);
  const acceptedShift = findShiftForRider(calendar, swap.toDay, swap.firstRiderAccepted);
  if (!fromShift || !acceptedShift) {
    return { success: false, status: 409, error: "Lo swap non corrisponde piu' al calendario" };
  }

  fromShift.shift.code = swap.firstRiderAccepted;
  fromShift.shift.color = await calculateShiftColor(swap.firstRiderAccepted, swap.fromDay, targetIsNext);
  acceptedShift.shift.code = swap.fromRider;
  acceptedShift.shift.color = await calculateShiftColor(swap.fromRider, swap.toDay, targetIsNext);

  await CalendarModel.updateOne(
    { _id: calendar._id },
    { $set: { days: calendar.days, lastUpdate: getServerTime() } }
  );
  return { success: true };
}

app.get("/api/generateCalendar", async (req, res) => {
  if (req.authUser.isAdmin !== true) return res.status(403).json({ error: "Solo admin" });
  const result = await generateOptimalCalendar(true);
  if (result.success) res.json({ message: "Successo" });
  else res.status(500).json({ error: result.error });
});

app.get("/api/generateCurrentCalendar", async (req, res) => {
  try {
      if (req.authUser.isAdmin !== true) return res.status(403).json({ error: "Solo admin" });
      const result = await generateOptimalCalendar(false);
      if (result.success) {
        await getModel("swaps").deleteMany({ isNext: false });
        res.json({ message: "Successo" });
      }
      else res.status(500).json({ error: result.error });
  } catch (e) {
      res.status(500).json({ error: e.message });
  }
});

app.post("/api/swaps/accept", async (req, res) => {
  try {
    const { swapId } = req.body;
    if (!mongoose.Types.ObjectId.isValid(swapId)) return res.status(400).json({ success: false, error: "Swap non valido" });

    const rider = Number(req.authUser.code);
    if (req.authUser.isAdmin === true) return res.status(403).json({ success: false, error: "Solo rider" });
    const swap = await getModel("swaps").findOne({
      _id: new mongoose.Types.ObjectId(swapId),
      firstRiderAccepted: -1
    }).lean();
    if (!swap) return res.status(409).json({ success: false, error: "Gia' accettato" });
    if (Number(swap.fromRider) === rider) return res.status(403).json({ success: false, error: "Accesso negato" });
    const adjustedToday = getTodayIndex();
    if (swap.isNext !== true && (Number(swap.fromDay) < adjustedToday || Number(swap.toDay) < adjustedToday)) {
      return res.status(409).json({ success: false, error: "Lo swap riguarda un giorno gia' passato" });
    }

    const calendar = await getModel("calendars").findOne({ isNext: swap.isNext === true }).lean();
    if (!calendar) return res.status(404).json({ success: false, error: "Calendario non trovato" });
    if (swap.isNext === true && !isCalendarPublishedForRiders(calendar)) {
      return res.status(403).json({ success: false, error: "Il calendario della prossima settimana non e' ancora pubblicato" });
    }
    const validationError = validateSwapCalendarState(calendar, swap, rider);
    if (validationError) return res.status(409).json({ success: false, error: validationError });

    const result = await getModel("swaps").findOneAndUpdate(
      { _id: new mongoose.Types.ObjectId(swapId), firstRiderAccepted: -1 },
      { $set: { firstRiderAccepted: rider, isReadyForAdmin: true, acceptedAt: getServerTime() } },
      { new: true }
    );
    if (result) res.json({ success: true, swap: result });
    else res.status(409).json({ success: false, error: "Gia' accettato" });
  } catch (error) { res.status(500).json({ error: error.message }); }
});

app.post("/api/swaps/approve", async (req, res) => {
  try {
    const isAdmin = req.authUser.isAdmin === true;
    if (!isAdmin) return res.status(403).json({ success: false, error: "Solo admin" });

    const { swapId } = req.body;
    if (!mongoose.Types.ObjectId.isValid(swapId)) return res.status(400).json({ success: false, error: "Swap non valido" });

    const SwapModel = getModel("swaps");
    const swap = await SwapModel.findOne({
      _id: new mongoose.Types.ObjectId(swapId),
      isReadyForAdmin: true,
      firstRiderAccepted: { $ne: -1 }
    }).lean();
    if (!swap) return res.status(404).json({ success: false, error: "Swap non trovato" });
    const adjustedToday = getTodayIndex();
    if (swap.isNext !== true && (Number(swap.fromDay) < adjustedToday || Number(swap.toDay) < adjustedToday)) {
      return res.status(409).json({ success: false, error: "Lo swap riguarda un giorno gia' passato" });
    }

    const result = await applyApprovedSwap(swap);
    if (!result.success) return res.status(result.status || 500).json(result);

    await SwapModel.deleteOne({ _id: swap._id });
    res.json({ success: true });
  } catch (error) { res.status(500).json({ success: false, error: error.message }); }
});

async function performWeeklyRotation(session) {
  const CalendarModel = getModel("calendars");
  const WeekStructureModel = getModel("weekStructure");
  const nextCal = await applySession(CalendarModel.findOne({ isNext: true }).lean(), session);
  if (nextCal) {
    delete nextCal._id;
    nextCal.isNext = false;
    nextCal.lastUpdate = getServerTime();
    await CalendarModel.updateOne(
      { isNext: false },
      { $set: nextCal },
      sessionOptions(session, { upsert: true })
    );
  }

  const nextWeekStructure = await getWeekStructureForCalendar(true, session);
  const currentWeekStructure = normalizeWeekStructure(nextWeekStructure, false);
  delete currentWeekStructure._id;
  await WeekStructureModel.updateOne(
    { isNext: false },
    { $set: currentWeekStructure },
    sessionOptions(session, { upsert: true })
  );

  await getModel("swaps").deleteMany({ isNext: false }, sessionOptions(session));
  await getModel("swaps").updateMany({ isNext: true }, { $set: { isNext: false } }, sessionOptions(session));
  await getModel("constraints").deleteMany({ isNext: false }, sessionOptions(session));
  await getModel("constraints").updateMany(
    { isNext: true },
    { $set: { isNext: false, updatedAt: getServerTime() } },
    sessionOptions(session)
  );

  const permanentConstraints = await applySession(
    getModel("constraints").find({ isNext: false, permanent: true }).lean(),
    session
  );
  if (permanentConstraints.length > 0) {
    await getModel("constraints").insertMany(permanentConstraints.map(constraint => {
      delete constraint._id;
      return { ...constraint, isNext: true, updatedAt: getServerTime() };
    }), sessionOptions(session));
  }

  const generationResult = await generateOptimalCalendar(true, session);
  if (!generationResult.success) {
    throw new Error(generationResult.error || "Generazione del nuovo calendario fallita");
  }
}

app.get("/api/cron/rotate-week", async (req, res) => {
  const StateModel = getModel("automationState");
  const weekKey = getCurrentWeekKey();
  const staleBefore = new Date(Date.now() - 30 * 60 * 1000);
  let lockAcquired = false;

  try {
    await StateModel.updateOne(
      { key: "weeklyRotation" },
      { $setOnInsert: { lastRotationWeek: null, rotationInProgress: false } },
      { upsert: true }
    );

    const currentState = await StateModel.findOne({ key: "weeklyRotation" }).lean();
    if (currentState?.lastRotationWeek === weekKey) {
      return res.json({ message: "Rotation already completed", skipped: true, week: weekKey });
    }

    const lock = await StateModel.findOneAndUpdate(
      {
        key: "weeklyRotation",
        lastRotationWeek: { $ne: weekKey },
        $or: [
          { rotationInProgress: false },
          { rotationInProgress: { $exists: false } },
          { rotationStartedAt: { $lt: staleBefore } }
        ]
      },
      { $set: { rotationInProgress: true, rotationStartedAt: new Date(), rotationAttemptWeek: weekKey } },
      { new: true }
    );
    if (!lock) return res.status(409).json({ error: "Rotazione gia' in corso" });
    lockAcquired = true;

    const dbSession = await mongoose.startSession();
    try {
      await dbSession.withTransaction(async () => {
        await performWeeklyRotation(dbSession);
        await StateModel.updateOne(
          { key: "weeklyRotation" },
          {
            $set: { lastRotationWeek: weekKey, rotationInProgress: false, rotationCompletedAt: new Date() },
            $unset: { rotationAttemptWeek: "", rotationStartedAt: "" }
          },
          { session: dbSession }
        );
      });
    } finally {
      await dbSession.endSession();
    }

    lockAcquired = false;
    res.json({ message: "Rotation ok", week: weekKey });
  } catch (error) {
    if (lockAcquired) {
      await StateModel.updateOne(
        { key: "weeklyRotation" },
        { $set: { rotationInProgress: false, rotationFailedAt: new Date(), rotationError: error.message } }
      ).catch(() => {});
    }
    res.status(500).json({ error: error.message });
  }
});

app.get("/api/:name", async (req, res) => {
  try {
    const collectionName = canonicalCollectionName(req.params.name);
    if (!collectionName) return res.status(404).json({ error: "Risorsa non supportata" });
    const Model = getModel(collectionName);
    let data = collectionName === "weekStructure"
      ? await getWeekStructures()
      : await Model.find({}).lean();
    const isAdmin = req.authUser.isAdmin === true;
    const userCode = Number(req.authUser.code);

    if (collectionName === "users") {
      data = data.map(u => {
        delete u.password;
        if (!isAdmin && Number(u.code) !== userCode) {
            delete u.email; delete u.phone; delete u.lastAccess;
        }
        return u;
      });
    }
    if (collectionName === "calendars") {
      if (!isAdmin) data = data.filter(cal => cal.isNext !== true || isCalendarPublishedForRiders(cal));
      for (const cal of data) {
        for (const workday of cal.days || []) {
          for (const shift of workday.listShift || []) {
            shift.color = await calculateShiftColor(shift.code, workday.day, cal.isNext);
          }
        }
        if (!isAdmin) delete cal.anomalies;
      }
    }
    if (collectionName === "constraints" && !isAdmin) data = data.filter(c => Number(c.riderCode) === userCode);
    if (collectionName === "swaps" && !isAdmin) {
      const calendars = await getModel("calendars").find({}).lean();
      data = data.filter(s => {
        const calendar = calendars.find(c => c.isNext === (s.isNext === true));
        if (!calendar || (s.isNext === true && !isCalendarPublishedForRiders(calendar))) return false;
        if (Number(s.fromRider) === userCode || Number(s.firstRiderAccepted) === userCode) return true;
        if (s.isReadyForAdmin || Number(s.firstRiderAccepted) !== -1) return false;
        return Boolean(findShiftForRider(calendar, s.toDay, userCode)) && !findShiftForRider(calendar, s.fromDay, userCode);
      });
    }
    res.json(data);
  } catch (error) { res.status(500).json({ error: error.message }); }
});

app.post("/api/:name", async (req, res) => {
  try {
    const isAdmin = req.authUser.isAdmin === true;
    const userCode = Number(req.authUser.code);
    const collectionName = canonicalCollectionName(req.params.name);
    if (!collectionName) return res.status(404).json({ error: "Risorsa non supportata" });
    const Model = getModel(collectionName);
    let dataArray = req.body;
    const now = getServerTime();

    if (collectionName === "users") {
      if (!isAdmin) {
          const finalData = Array.isArray(dataArray) ? dataArray : [dataArray];
          const onlyOwnLastAccess = finalData.every(u =>
            Number(u.code) === userCode &&
            u.lastAccess === true &&
            Object.keys(u).every(key => ["code", "lastAccess"].includes(key))
          );
          if (!onlyOwnLastAccess) return res.status(403).json({ error: "Solo admin" });
      }
      if (Array.isArray(dataArray)) {
          for (let u of dataArray) {
            let updateObj = { ...u, updatedAt: now };
            if (u.lastAccess === true) { await Model.updateOne({ code: u.code }, { $set: { lastAccess: now, updatedAt: now } }); continue; }
            if (!isAdmin) return res.status(403).json({ error: "Solo admin" });
            if (!u.password) delete updateObj.password;
            else if (!u.password.startsWith("$2")) updateObj.password = await bcrypt.hash(u.password, 10);
            await Model.updateOne({ code: u.code }, { $set: updateObj }, { upsert: true });
            if (u.password) await revokeUserSessions(u.code);
          }
          return res.json({ success: true });
      } else {
          if (!isAdmin) {
            await Model.updateOne({ code: userCode }, { $set: { lastAccess: now, updatedAt: now } });
            return res.json({ success: true });
          }
          let u = dataArray;
          const normalizedEmail = u.email ? u.email.toLowerCase().trim() : "";
          if (!normalizedEmail || u.code === undefined) return res.status(400).json({ error: "Dati utente non validi" });
          const duplicate = await Model.findOne({
            $or: [{ email: normalizedEmail }, { code: parseInt(u.code) }]
          }).lean();
          if (duplicate) return res.status(409).json({ error: "Email o codice rider gia' esistente" });
          u.email = normalizedEmail;
          u.code = parseInt(u.code);
          let updateObj = { ...u, updatedAt: now };
          if (u.password && !u.password.startsWith("$2")) updateObj.password = await bcrypt.hash(u.password, 10);
          await Model.updateOne({ code: u.code }, { $set: updateObj }, { upsert: true });
          return res.json({ success: true });
      }
    }

    if (collectionName === "constraints" && !isAdmin) {
        const nextWeekStructure = await getWeekStructureForCalendar(true);
        const adjustedToday = getTodayIndex();
        if (adjustedToday > nextWeekStructure.lastDayConstraint) {
            return res.status(403).json({ error: "Il publication day e' stato superato" });
        }
        const finalData = Array.isArray(dataArray) ? dataArray : [dataArray];
        for (let c of finalData) {
            if (parseInt(c.riderCode) !== userCode) return res.status(403).json({ error: "Accesso negato" });
            if (c.isNext === false) return res.status(403).json({ error: "I vincoli della settimana corrente sono in sola lettura" });
        }
        await Model.deleteMany({ riderCode: userCode, isNext: true });
        if (finalData.length === 0) return res.json({ success: true, count: 0 });
        const result = await Model.insertMany(finalData.map(item => ({ ...item, riderCode: userCode, isNext: item.isNext !== false, updatedAt: now })));
        return res.json({ success: true, count: result.length });
    }

    if (collectionName === "swaps") {
        if (isAdmin) return res.status(403).json({ error: "Solo rider" });
        const swaps = Array.isArray(dataArray) ? dataArray : [dataArray];
        for (let s of swaps) {
            if (parseInt(s.fromRider) !== userCode) return res.status(403).json({ error: "Accesso negato" });
            const fromDay = Number(s.fromDay);
            const toDay = Number(s.toDay);
            if (!Number.isInteger(fromDay) || !Number.isInteger(toDay) || fromDay < 0 || fromDay > 6 || toDay < 0 || toDay > 6 || fromDay === toDay) {
                return res.status(400).json({ error: "Giorni swap non validi" });
            }
            const targetIsNext = s.isNext === true;
            const adjustedToday = getTodayIndex();
            if (!targetIsNext && (fromDay < adjustedToday || toDay < adjustedToday)) {
                return res.status(409).json({ error: "Lo swap riguarda un giorno gia' passato" });
            }
            const calendar = await getModel("calendars").findOne({ isNext: targetIsNext }).lean();
            if (!calendar) return res.status(404).json({ error: "Calendario non trovato" });
            if (targetIsNext && !isCalendarPublishedForRiders(calendar)) {
                return res.status(403).json({ error: "Il calendario della prossima settimana non e' ancora pubblicato" });
            }
            const validationError = validateSwapCalendarState(calendar, s);
            if (validationError) return res.status(409).json({ error: validationError });
            s.fromDay = fromDay;
            s.toDay = toDay;
            s.isNext = targetIsNext;
            s.requestDate = now; s.isReadyForAdmin = false; s.firstRiderAccepted = -1;
        }
        const result = await Model.insertMany(swaps);
        return res.json({ success: true, count: result.length });
    }

    if (!isAdmin) return res.status(403).json({ error: "Solo admin" });

    if (collectionName === "calendars") {
        const calendars = Array.isArray(dataArray) ? dataArray : [dataArray];
        for (const cal of calendars) {
            for (let workday of cal.days) {
              for (let shift of workday.listShift) {
                shift.color = await calculateShiftColor(shift.code, workday.day, cal.isNext);
              }
            }
            cal.anomalies = await calculateCalendarAnomalies(cal);
            await Model.updateOne({ isNext: cal.isNext }, { $set: { ...cal, lastUpdate: now } }, { upsert: true });
        }
        return res.json({ success: true });
    }
    if (collectionName === "weekStructure") {
        const sharedLastDayConstraint = (
            dataArray.find(item => item.isNext === true) || dataArray[0] || {}
        ).lastDayConstraint;
        const adjustedToday = getTodayIndex();
        if (sharedLastDayConstraint !== undefined && sharedLastDayConstraint < adjustedToday) {
            return res.status(400).json({ error: "Il publication day non può essere impostato su un giorno già passato" });
        }
        for (const structure of dataArray) {
            const normalized = normalizeWeekStructure(structure, structure.isNext === true);
            if (sharedLastDayConstraint !== undefined) {
                normalized.lastDayConstraint = sharedLastDayConstraint;
            }
            delete normalized._id;
            if (normalized.lastDayConstraint !== undefined) {
                await getModel("calendars").updateOne(
                    { isNext: normalized.isNext },
                    { $set: { publicationDay: normalized.lastDayConstraint } }
                );
            }
            await Model.updateOne(
                { isNext: normalized.isNext },
                { $set: { ...normalized, updatedAt: now } },
                { upsert: true }
            );
        }
        return res.json({ success: true });
    }
    res.status(400).json({ error: "Collezione non supportata" });
  } catch (error) { res.status(500).json({ error: error.message }); }
});

app.delete("/api/:name/:id", async (req, res) => {
    try {
        const isAdmin = req.authUser.isAdmin === true;
        const userCode = Number(req.authUser.code);
        const collectionName = canonicalCollectionName(req.params.name);
        if (!collectionName) return res.status(404).json({ error: "Risorsa non supportata" });
        const id = req.params.id;
        const Model = getModel(collectionName);

        if (collectionName === "users") {
            if (!isAdmin) return res.status(403).json({ error: "Solo admin" });
            const code = parseInt(id);
            if (!Number.isInteger(code)) return res.status(400).json({ error: "Codice rider non valido" });
            const targetUser = await Model.findOne({ code }).lean();
            if (!targetUser) return res.status(404).json({ error: "Rider non trovato" });
            if (targetUser.isAdmin === true) return res.status(400).json({ error: "L'admin non puo' essere eliminato" });
            await replaceDeletedRiderInCalendars(code);
            await getModel("constraints").deleteMany({ riderCode: code });
            await getModel("swaps").deleteMany({ $or: [{ fromRider: code }, { firstRiderAccepted: code }] });
            await revokeUserSessions(code);
            const result = await Model.deleteOne({ code: code });
            return res.json({ success: result.deletedCount > 0 });
        }

        if (collectionName === "swaps") {
            const query = isAdmin ? { _id: id } : { _id: id, fromRider: userCode };
            const result = await Model.deleteOne(query);
            return res.json({ success: result.deletedCount > 0 });
        }

        res.status(400).json({ error: "Collezione non supportata per delete" });
    } catch (error) { res.status(500).json({ error: error.message }); }
});

app.listen(PORT, () => console.log(`Server operativo su porta ${PORT}`));
