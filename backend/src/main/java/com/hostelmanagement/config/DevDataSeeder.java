package com.hostelmanagement.config;

import com.hostelmanagement.admission.AdmissionService;
import com.hostelmanagement.admission.dto.AdmissionDtos.AdmitRequest;
import com.hostelmanagement.admission.dto.AdmissionDtos.CheckoutRequest;
import com.hostelmanagement.admission.dto.AdmissionDtos.TransferRequest;
import com.hostelmanagement.auth.Role;
import com.hostelmanagement.auth.RoleRepository;
import com.hostelmanagement.auth.User;
import com.hostelmanagement.auth.UserManagementService;
import com.hostelmanagement.auth.UserManagementService.CreateUserRequest;
import com.hostelmanagement.auth.UserRepository;
import com.hostelmanagement.booking.BookingService;
import com.hostelmanagement.booking.BookingService.BookingRequest;
import com.hostelmanagement.building.BuildingService;
import com.hostelmanagement.building.BuildingService.BuildingRequest;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.enquiry.EnquiryService;
import com.hostelmanagement.enquiry.EnquiryService.EnquiryRequest;
import com.hostelmanagement.expenses.ExpenseService;
import com.hostelmanagement.expenses.ExpenseService.ExpenseRequest;
import com.hostelmanagement.floor.FloorService;
import com.hostelmanagement.hostel.HostelService;
import com.hostelmanagement.hostel.HostelType;
import com.hostelmanagement.hostel.dto.HostelDtos.HostelRequest;
import com.hostelmanagement.maintenance.MaintenanceService;
import com.hostelmanagement.maintenance.MaintenanceService.MaintenanceRequestDto;
import com.hostelmanagement.occupancy.BuildingViewService;
import com.hostelmanagement.occupancy.BedState;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.BedView;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.RoomView;
import com.hostelmanagement.owner.Owner;
import com.hostelmanagement.owner.OwnerRepository;
import com.hostelmanagement.owner.OwnerSettings;
import com.hostelmanagement.owner.OwnerSettingsRepository;
import com.hostelmanagement.payments.PaymentQueries;
import com.hostelmanagement.payments.PaymentService;
import com.hostelmanagement.payments.PaymentService.RecordPaymentRequest;
import com.hostelmanagement.payments.dto.FinanceDtos.InvoiceDto;
import com.hostelmanagement.resident.dto.ResidentDtos.ResidentRequest;
import com.hostelmanagement.room.RoomService;
import com.hostelmanagement.room.RoomService.RoomRequest;
import com.hostelmanagement.security.PrincipalLoader;
import com.hostelmanagement.staff.StaffService;
import com.hostelmanagement.staff.StaffService.StaffRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Development-only demo data: two owners, several hostels with buildings/floors/rooms/beds, residents with
 * staggered admissions and checkouts, invoices and payments (paid, partial, overdue, due today), bookings,
 * enquiries, maintenance, staff users and expenses. Everything is created through the real services so the data
 * obeys the same rules as user input. Runs only when the database contains no owners; never touches existing data.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private static final String[] FIRST = {"Aarav", "Vivaan", "Aditya", "Arjun", "Sai", "Reyansh", "Krishna", "Ishaan",
            "Rohan", "Kabir", "Ananya", "Diya", "Saanvi", "Aadhya", "Kavya", "Priya", "Meera", "Isha", "Neha", "Riya",
            "Rahul", "Vikram", "Karthik", "Nikhil", "Suresh", "Pooja", "Sneha", "Divya", "Lakshmi", "Harini",
            "Manoj", "Varun", "Akash", "Deepak", "Gaurav", "Tanvi", "Nisha", "Shreya", "Anjali", "Swati"};
    private static final String[] LAST = {"Sharma", "Verma", "Iyer", "Reddy", "Nair", "Patel", "Gupta", "Rao",
            "Menon", "Kulkarni", "Joshi", "Das", "Singh", "Kumar", "Pillai", "Shetty", "Bhat", "Mishra", "Chopra", "Naidu"};

    private final AppProperties props;
    private final OwnerRepository ownerRepository;
    private final OwnerSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.hostelmanagement.subscription.SubscriptionService subscriptionService;
    private final HostelService hostelService;
    private final BuildingService buildingService;
    private final FloorService floorService;
    private final RoomService roomService;
    private final BuildingViewService buildingViewService;
    private final AdmissionService admissionService;
    private final PaymentService paymentService;
    private final PaymentQueries paymentQueries;
    private final BookingService bookingService;
    private final EnquiryService enquiryService;
    private final MaintenanceService maintenanceService;
    private final StaffService staffService;
    private final ExpenseService expenseService;
    private final UserManagementService userManagementService;
    private final TransactionTemplate tx;
    private final AppClock clock;

    private final Random random = new Random(20260101L);
    private final Set<String> usedMobiles = new HashSet<>();
    private int nameIndex;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled() || ownerRepository.count() > 0) {
            return;
        }
        log.info("Seeding development demo data ...");
        long started = System.currentTimeMillis();
        try {
            User owner1 = createOwner("Sunrise Hostels Pvt Ltd", "owner@hostelhub.dev", "Ramesh Iyer", "9845012345");
            asUser(owner1, () -> seedOwnerOne(owner1));
            User owner2 = createOwner("Green Valley Stays", "owner2@hostelhub.dev", "Lakshmi Narayanan", "9840098400");
            asUser(owner2, this::seedOwnerTwo);
            log.info("Demo data seeded in {} ms. Sign in as owner@hostelhub.dev / Owner@123",
                    System.currentTimeMillis() - started);
        } catch (RuntimeException e) {
            log.error("Seeding demo data failed (the application keeps running)", e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ------------------------------------------------------------------ owner 1

    private void seedOwnerOne(User owner) {
        LocalDate today = clock.today();
        Long sunrise = hostel("Sunrise Boys Hostel", HostelType.BOYS, "12, 5th Cross, Koramangala 4th Block",
                "Bengaluru", "Karnataka", "560034", "8041234567");
        Long lotus = hostel("Lotus Girls Hostel", HostelType.GIRLS, "Plot 44, Model Colony, Shivajinagar",
                "Pune", "Maharashtra", "411016", "2025671234");
        Long nest = hostel("Urban Nest Co-Living", HostelType.CO_LIVING, "8-2-293, Road No. 12, Banjara Hills",
                "Hyderabad", "Telangana", "500034", "4023456789");

        // Sunrise: Block A (Ground + 4 floors, 4 rooms x 4 beds) and Block B (3 floors, 3 triple rooms)
        Long blockA = building(sunrise, "Block A", "Main block with 4-sharing rooms", 5);
        Long blockB = building(sunrise, "Block B", "Annexe with 3-sharing rooms", 3);
        for (var f : floorService.list(blockA)) {
            for (int i = 1; i <= 4; i++) {
                room(f.id(), roomNumber(f.levelNumber(), i), "4 Sharing", 4, "8000", "10000", "Wi-Fi, Attached bathroom, Study table");
            }
        }
        for (var f : floorService.list(blockB)) {
            for (int i = 1; i <= 3; i++) {
                room(f.id(), "B" + roomNumber(f.levelNumber(), i), "3 Sharing", 3, "9000", "12000", "Wi-Fi, AC, Balcony");
            }
        }
        // Lotus: one building, 4 floors, 5 rooms of 2 or 3 beds
        Long lotusMain = building(lotus, "Main Building", null, 4);
        for (var f : floorService.list(lotusMain)) {
            for (int i = 1; i <= 5; i++) {
                boolean twin = i % 2 == 0;
                room(f.id(), roomNumber(f.levelNumber(), i), twin ? "2 Sharing" : "3 Sharing", twin ? 2 : 3,
                        twin ? "9500" : "8500", "10000", "Wi-Fi, Wardrobe, Hot water");
            }
        }
        // Urban Nest: one tower, 3 floors, singles and doubles
        Long tower = building(nest, "Tower 1", "Premium co-living", 3);
        for (var f : floorService.list(tower)) {
            for (int i = 1; i <= 4; i++) {
                boolean single = i <= 2;
                room(f.id(), roomNumber(f.levelNumber(), i), single ? "Single" : "2 Sharing", single ? 1 : 2,
                        single ? "15000" : "11000", "20000", "Wi-Fi, AC, Housekeeping, Gym");
            }
        }

        // Staff logins
        userManagementService.create(new CreateUserRequest("Suresh Kumar", "manager@hostelhub.dev", "9900112233",
                "Manager@123", "MANAGER", null, Set.of(sunrise, lotus)));
        userManagementService.create(new CreateUserRequest("Kavita Joshi", "warden@hostelhub.dev", "9900334455",
                "Warden@123", "WARDEN", null, Set.of(lotus)));
        var plumber = staffService.create(new StaffRequest(sunrise, null, "Mahesh Gowda", "9886001122", "Plumber",
                new BigDecimal("18000"), today.minusYears(2), "ACTIVE"));
        staffService.create(new StaffRequest(sunrise, null, "Ravi Shankar", "9886002233", "Electrician",
                new BigDecimal("20000"), today.minusYears(1), "ACTIVE"));
        staffService.create(new StaffRequest(lotus, null, "Sunita Pawar", "9822003344", "Housekeeping",
                new BigDecimal("14000"), today.minusMonths(8), "ACTIVE"));
        staffService.create(new StaffRequest(nest, null, "Imran Khan", "9848004455", "Caretaker",
                new BigDecimal("22000"), today.minusMonths(14), "ACTIVE"));

        // Maintenance: whole room 302 in Block A is blocked (kept empty), one bed in Lotus is blocked
        RoomView r302 = roomByNumber(sunrise, "302");
        maintenanceService.create(new MaintenanceRequestDto(sunrise, r302.id(), null, "Seepage in bathroom wall",
                "Water seepage from the bathroom wall; re-plastering and waterproofing required.", "Plumbing",
                "HIGH", true, new BigDecimal("15000"), plumber.id()));

        // Example floor exactly as on the approved dashboard: 401 (3/4), 402 (2/4), 403 (1/4, vacating soon), 404 (0/4)
        fillRoom(sunrise, "401", 3, null);
        fillRoom(sunrise, "402", 2, null);
        fillRoom(sunrise, "403", 1, today.plusDays(3));
        Set<String> handled = Set.of("401", "402", "403", "404", "302");

        // Historical stays (checked out) to build admission history, then the regular population
        historicalStays(sunrise, List.of("101", "203"));
        populate(sunrise, handled, 0.72);
        populate(lotus, Set.of(), 0.75);
        populate(nest, Set.of(), 0.65);

        RoomView lotusRoom = roomByNumber(lotus, "203");
        BedView freeLotusBed = lotusRoom.beds().stream().filter(b -> b.state() == BedState.AVAILABLE).findFirst().orElse(null);
        if (freeLotusBed != null) {
            maintenanceService.create(new MaintenanceRequestDto(lotus, lotusRoom.id(), freeLotusBed.id(),
                    "Broken bed frame", "Bed frame cracked; replacement ordered.", "Furniture", "MEDIUM", true,
                    new BigDecimal("4500"), null));
        }
        maintenanceService.create(new MaintenanceRequestDto(nest, roomByNumber(nest, "101").id(), null,
                "AC not cooling", "Split AC needs gas refill.", "Electrical", "LOW", false, new BigDecimal("2500"), null));

        // One bed transfer for history
        transferOne(sunrise);

        // Bookings on currently free beds
        reserveFreeBed(nest, "Farhan Ali", today.plusDays(6));
        reserveFreeBed(lotus, "Ritika Sen", today.plusDays(12));

        // Enquiries
        enquiryService.create(new EnquiryRequest(sunrise, "Pranav Hegde", nextMobile(), "pranav@example.com", "Male",
                "4 Sharing", today.plusDays(10), new BigDecimal("8000"), "Walk-in", "NEW", today, "Wants ground floor", true));
        enquiryService.create(new EnquiryRequest(lotus, "Megha Bansal", nextMobile(), null, "Female", "2 Sharing",
                today.plusDays(20), new BigDecimal("9500"), "Website", "FOLLOW_UP", today.plusDays(2), "Visiting on weekend", true));
        enquiryService.create(new EnquiryRequest(nest, "Siddharth Rao", nextMobile(), "sid@example.com", "Male",
                "Single", today.plusDays(5), new BigDecimal("15000"), "Referral", "CONTACTED", today.minusDays(1), null, false));
        enquiryService.create(new EnquiryRequest(null, "Aisha Khan", nextMobile(), null, "Female", "Any",
                null, null, "Phone", "LOST", null, "Found a place closer to college", false));

        // Expenses for the last 6 months
        for (int m = 5; m >= 0; m--) {
            LocalDate d = today.minusMonths(m).withDayOfMonth(Math.min(5, today.getDayOfMonth()));
            if (d.isAfter(today)) {
                continue;
            }
            expense(sunrise, "ELECTRICITY", "Electricity bill", 38000 + random.nextInt(9000), d);
            expense(sunrise, "WATER", "Water tanker & BWSSB", 9000 + random.nextInt(3000), d);
            expense(sunrise, "SALARY", "Staff salaries", 38000, d);
            expense(lotus, "ELECTRICITY", "Electricity bill", 24000 + random.nextInt(6000), d);
            expense(lotus, "FOOD", "Groceries for mess", 52000 + random.nextInt(8000), d);
            expense(nest, "ELECTRICITY", "Electricity bill", 21000 + random.nextInt(5000), d);
            expense(nest, "INTERNET", "Broadband", 4500, d);
        }
    }

    // ------------------------------------------------------------------ owner 2 (for isolation testing)

    private void seedOwnerTwo() {
        Long gv = hostel("Green Valley PG", HostelType.CO_LIVING, "21, Gandhi Road, T. Nagar", "Chennai",
                "Tamil Nadu", "600017", "4428153000");
        Long b = building(gv, "Main Block", null, 2);
        for (var f : floorService.list(b)) {
            for (int i = 1; i <= 3; i++) {
                room(f.id(), roomNumber(f.levelNumber(), i), "2 Sharing", 2, "7500", "7500", "Wi-Fi");
            }
        }
        populate(gv, Set.of(), 0.6);
    }

    // ------------------------------------------------------------------ population helpers

    /** Admits residents into a share of free beds, with varied stay length, checkout dates and payment behaviour. */
    private void populate(Long hostelId, Set<String> skipRooms, double occupancy) {
        LocalDate today = clock.today();
        int index = 0;
        for (RoomView room : buildingViewService.rooms(hostelId, null, null, null, null)) {
            if (skipRooms.contains(room.roomNumber()) || room.roomUnderMaintenance()) {
                continue;
            }
            for (BedView bed : room.beds()) {
                if (bed.state() != BedState.AVAILABLE || random.nextDouble() > occupancy) {
                    continue;
                }
                index++;
                LocalDate admission;
                if (index % 9 == 0) {
                    // Billing period starts 5 days ago -> rent due today (grace period is 5 days)
                    admission = today.minusDays(5).minusMonths(1 + random.nextInt(4));
                } else {
                    admission = today.minusDays(12 + random.nextInt(230));
                }
                LocalDate checkout = null;
                boolean confirmed = false;
                int roll = random.nextInt(100);
                if (roll < 7) {
                    checkout = today.plusDays(1 + random.nextInt(7));
                    confirmed = random.nextBoolean();
                } else if (roll < 13) {
                    checkout = today.plusDays(8 + random.nextInt(8));
                    confirmed = random.nextBoolean();
                } else if (roll < 20) {
                    checkout = today.plusDays(16 + random.nextInt(15));
                } else if (roll < 70) {
                    checkout = today.plusDays(60 + random.nextInt(240));
                }
                if (checkout != null && !checkout.isAfter(admission)) {
                    checkout = admission.plusMonths(6);
                }
                Long residentId = admit(bed.id(), hostelId, admission, checkout, confirmed);
                payDues(hostelId, residentId, random.nextInt(100));
            }
        }
    }

    private void fillRoom(Long hostelId, String roomNumber, int count, LocalDate checkoutForFirst) {
        RoomView room = roomByNumber(hostelId, roomNumber);
        LocalDate today = clock.today();
        int n = 0;
        for (BedView bed : room.beds()) {
            if (n >= count) {
                break;
            }
            LocalDate checkout = n == 0 ? checkoutForFirst : today.plusMonths(4 + n);
            Long residentId = admit(bed.id(), hostelId, today.minusDays(40 + 25L * n), checkout, n == 0 && checkoutForFirst != null);
            payDues(hostelId, residentId, 10 + n * 30);
            n++;
        }
    }

    private void historicalStays(Long hostelId, List<String> rooms) {
        LocalDate today = clock.today();
        for (String rn : rooms) {
            RoomView room = roomByNumber(hostelId, rn);
            BedView bed = room.beds().get(0);
            LocalDate admission = today.minusDays(300);
            Long admissionId = admitAdmission(bed.id(), hostelId, admission, admission.plusMonths(6), true);
            Long residentId = tx.execute(s -> admissionService.getEntity(admissionId).getResidentId());
            payDues(hostelId, residentId, 0);
            admissionService.checkout(admissionId, new CheckoutRequest(admission.plusMonths(6).minusDays(1), "Course completed"));
        }
    }

    private void transferOne(Long hostelId) {
        List<RoomView> rooms = buildingViewService.rooms(hostelId, null, null, null, null);
        BedView from = null;
        BedView to = null;
        for (RoomView r : rooms) {
            for (BedView b : r.beds()) {
                if (from == null && b.state() == BedState.OCCUPIED && b.resident().admissionDate()
                        .isBefore(clock.today().minusDays(30)) && !Set.of("401", "402", "403").contains(r.roomNumber())) {
                    from = b;
                } else if (to == null && b.state() == BedState.AVAILABLE && !r.roomUnderMaintenance()
                        && !Set.of("404", "302").contains(r.roomNumber())) {
                    to = b;
                }
            }
        }
        if (from != null && to != null) {
            admissionService.transfer(from.resident().admissionId(), new TransferRequest(to.id(),
                    clock.today().minusDays(10), null, "Requested a quieter room"));
        }
    }

    private void reserveFreeBed(Long hostelId, String guest, LocalDate date) {
        for (RoomView r : buildingViewService.rooms(hostelId, null, null, "HAS_AVAILABLE", null)) {
            for (BedView b : r.beds()) {
                if (b.state() == BedState.AVAILABLE) {
                    bookingService.create(new BookingRequest(b.id(), null, guest, nextMobile(), date,
                            date.plusDays(3), new BigDecimal("2000"), "Advance to be collected on arrival"));
                    return;
                }
            }
        }
    }

    /** behaviour: 0-69 pays everything, 70-84 leaves the latest invoice part-paid, 85-99 leaves 1-2 rent invoices unpaid. */
    private void payDues(Long hostelId, Long residentId, int behaviour) {
        List<InvoiceDto> open = paymentQueries.searchInvoices(List.of(hostelId), residentId, "OPEN", null, null, null,
                null, clock.today(), 0, 100).content();
        int unpaidTail = behaviour >= 85 ? 1 + random.nextInt(2) : 0;
        for (int i = 0; i < open.size(); i++) {
            InvoiceDto inv = open.get(i);
            boolean last = i == open.size() - 1;
            if (i >= open.size() - unpaidTail && "RENT".equals(inv.invoiceType())) {
                continue;
            }
            BigDecimal amount = inv.balance();
            if (behaviour >= 70 && behaviour < 85 && last) {
                amount = amount.divide(BigDecimal.valueOf(2), 0, java.math.RoundingMode.DOWN);
            }
            // Current-period invoices that are not yet due are only paid by some residents
            if (last && inv.dueDate().isAfter(clock.today()) && random.nextInt(100) < 40) {
                continue;
            }
            if (amount.signum() <= 0) {
                continue;
            }
            LocalDate date = inv.dueDate().minusDays(random.nextInt(4));
            if (date.isAfter(clock.today())) {
                date = clock.today();
            }
            if (inv.periodStart() != null && date.isBefore(inv.periodStart().minusDays(3))) {
                date = inv.periodStart();
            }
            String[] methods = {"UPI", "UPI", "UPI", "CASH", "BANK_TRANSFER", "CARD"};
            String method = methods[random.nextInt(methods.length)];
            paymentService.collect(new RecordPaymentRequest(residentId, amount, date, method,
                    "CASH".equals(method) ? null : "TXN" + (100000 + random.nextInt(899999)), null,
                    List.of(new PaymentService.AllocationRequest(inv.id(), amount)), false, null));
        }
    }

    private Long admit(Long bedId, Long hostelId, LocalDate admission, LocalDate checkout, boolean confirmed) {
        Long admissionId = admitAdmission(bedId, hostelId, admission, checkout, confirmed);
        return tx.execute(s -> admissionService.getEntity(admissionId).getResidentId());
    }

    private Long admitAdmission(Long bedId, Long hostelId, LocalDate admission, LocalDate checkout, boolean confirmed) {
        String first = FIRST[nameIndex % FIRST.length];
        String last = LAST[(nameIndex * 7 + 3) % LAST.length];
        nameIndex++;
        boolean female = List.of("Ananya", "Diya", "Saanvi", "Aadhya", "Kavya", "Priya", "Meera", "Isha", "Neha",
                "Riya", "Pooja", "Sneha", "Divya", "Lakshmi", "Harini", "Tanvi", "Nisha", "Shreya", "Anjali", "Swati")
                .contains(first);
        String name = first + " " + last;
        ResidentRequest resident = new ResidentRequest(hostelId, name, nextMobile(), null,
                first.toLowerCase() + "." + last.toLowerCase() + nameIndex + "@example.com",
                clock.today().minusYears(18 + random.nextInt(8)).minusDays(random.nextInt(300)),
                female ? "Female" : "Male", "Hometown address, " + List.of("Mysuru", "Nagpur", "Kochi", "Vijayawada",
                "Indore", "Coimbatore").get(random.nextInt(6)),
                "Parent of " + first, nextMobile(), "AADHAAR", "XXXX-XXXX-" + (1000 + random.nextInt(8999)),
                "Parent of " + first, nextMobile(), "Parent", random.nextBoolean() ? "Student" : "Working professional",
                random.nextInt(100) < 80, null);
        return admissionService.admit(new AdmitRequest(null, resident, bedId, admission, checkout, confirmed,
                null, null, "MONTHLY", null, null, null, null, false));
    }

    // ------------------------------------------------------------------ structure helpers

    private Long hostel(String name, HostelType type, String address, String city, String state, String pin, String phone) {
        return hostelService.create(new HostelRequest(name, type, address, city, state, pin, phone,
                null, null, "ACTIVE")).id();
    }

    private Long building(Long hostelId, String name, String description, int floors) {
        return buildingService.create(new BuildingRequest(hostelId, name, description, "ACTIVE", null, floors)).id();
    }

    private void room(Long floorId, String number, String type, int beds, String rent, String deposit, String amenities) {
        roomService.create(new RoomRequest(floorId, number, type, beds, new BigDecimal(rent), new BigDecimal(deposit),
                amenities, "AVAILABLE", null));
    }

    private RoomView roomByNumber(Long hostelId, String number) {
        return buildingViewService.rooms(hostelId, null, null, null, null).stream()
                .filter(r -> r.roomNumber().equals(number)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Room " + number + " not found"));
    }

    private static String roomNumber(int level, int i) {
        return level == 0 ? "G0" + i : String.valueOf(level * 100 + i);
    }

    private void expense(Long hostelId, String category, String description, int amount, LocalDate date) {
        expenseService.create(new ExpenseRequest(hostelId, category, description, new BigDecimal(amount), date,
                "BANK_TRANSFER", null, null, null));
    }

    private String nextMobile() {
        String m;
        do {
            m = (7 + random.nextInt(3)) + String.format("%09d", random.nextInt(1_000_000_000));
        } while (!usedMobiles.add(m));
        return m;
    }

    // ------------------------------------------------------------------ identity helpers

    private User createOwner(String company, String email, String fullName, String phone) {
        return tx.execute(s -> {
            Owner o = new Owner();
            o.setName(company);
            o.setEmail(email);
            o.setPhone(phone);
            ownerRepository.save(o);
            settingsRepository.save(OwnerSettings.defaults(o.getId()));
            subscriptionService.grandfather(o.getId());
            Role ownerRole = roleRepository.findByCode("OWNER").orElseThrow();
            User u = new User();
            u.setOwnerId(o.getId());
            u.setFullName(fullName);
            u.setEmail(email);
            u.setPhone(phone);
            u.setPasswordHash(passwordEncoder.encode("Owner@123"));
            u.setRoles(new HashSet<>(Set.of(ownerRole)));
            return userRepository.save(u);
        });
    }

    private void asUser(User user, Runnable action) {
        var principal = tx.execute(s -> PrincipalLoader.toAuthUser(userRepository.findWithAccessById(user.getId()).orElseThrow()));
        var authorities = new ArrayList<org.springframework.security.core.GrantedAuthority>();
        principal.roles().forEach(r -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r)));
        principal.permissions().forEach(p -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_" + p)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
